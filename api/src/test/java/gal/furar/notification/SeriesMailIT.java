// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractSeasonIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Os correos á entidade (ADR-0039, punto 12): tempada asignada coas datas, rematada, solicitude non
 * aprobada coa mensaxe e, de cada sesión, a cancelación do concello ao correo da entidade, sen o pé
 * «Se esta reserva non é túa». Un só correo por tempada, nunca un por sesión.
 */
class SeriesMailIT extends AbstractSeasonIT {

  @Autowired RecordingMailSender mails;

  List<MimeMessage> subjects(String address, String prefix) {
    return mails.sentTo(address).stream()
        .filter(
            m -> {
              try {
                return m.getSubject().startsWith(prefix);
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  static String body(MimeMessage message) {
    try {
      return WaitMailIT.text(message);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void theOrganizationReceivesOneMailPerSeasonAndTheSessionCancellations() throws Exception {
    Facility f = facility("correo-tempada", true);
    UUID use = whole(f, Map.of());
    Api.Response org = createOrganization(f, "Club Demo Baloncesto", List.of());
    assertThat(org.status()).isEqualTo(201);
    UUID club = id(org.json());
    String address = org.json().get("email").asString();
    LocalDate monday = nextWorking(1);
    UUID seriesId =
        created(f, schedule(club, f, use, null, List.of(1), 600, 690, monday, monday.plusDays(14)));

    await(() -> subjects(address, "Tempada asignada").size() == 1);
    String created = body(subjects(address, "Tempada asignada").getFirst());
    assertThat(created).contains("Club Demo Baloncesto").contains("3");
    // Nin unha confirmación por sesión.
    settle();
    assertThat(subjects(address, "Reserva confirmada")).isEmpty();

    // A cancelación dunha sesión polo concello, ao correo da entidade.
    UUID session =
        jdbc.sql("select id from booking where series_id = :s order by lower(period) limit 1")
            .param("s", seriesId)
            .query(UUID.class)
            .single();
    long version =
        api()
            .get(bookings(f.slug()) + "/" + session)
            .token(TestTokens.manager(f.slug()))
            .send()
            .etagVersion();
    Api.Response cancelled =
        api()
            .post(
                bookings(f.slug()) + "/" + session + "/cancel",
                Map.of("channels", List.of("EMAIL")))
            .ifMatch(version)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    await(() -> mails.sentTo(address).size() == 2);
    String cancellation = body(mails.sentTo(address).get(1));
    assertThat(cancellation).contains("Sesi").doesNotContain("non é túa");

    // Rematar: un só correo coas sesións canceladas.
    assertThat(end(f, seriesId, monday.plusDays(7)).status()).isEqualTo(200);
    await(() -> subjects(address, "Tempada rematada").size() == 1);
    settle();
    assertThat(mails.sentTo(address)).hasSize(3);
  }

  @Test
  void aRejectedRequestCarriesTheMessage() throws Exception {
    Facility f = facility("correo-solicitude", true);
    UUID use = whole(f, Map.of());
    String bea = "bea-" + UUID.randomUUID();
    Api.Response org = createOrganization(f, "Club Demo Patinaxe", List.of(email(bea)));
    UUID club = id(org.json());
    String address = org.json().get("email").asString();
    LocalDate thursday = nextWorking(4);
    Api.Response asked =
        request(
            person(bea),
            f,
            club,
            schedule(club, f, use, null, List.of(4), 600, 690, thursday, thursday.plusDays(7)));
    assertThat(asked.status()).as(asked.toString()).isEqualTo(201);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "Ese día xa hai outro club.");
    Api.Response rejected =
        api()
            .post(requests(f.slug()) + "/" + id(asked.json()) + "/reject", body)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(rejected.status()).as(rejected.toString()).isEqualTo(200);

    await(() -> subjects(address, "Solicitude de tempada non aprobada").size() == 1);
    assertThat(body(subjects(address, "Solicitude de tempada non aprobada").getFirst()))
        .contains("outro club");
    // Á persoa responsable non lle chega nada.
    assertThat(mails.sentTo(email(bea))).isEmpty();
  }
}
