// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractWaitIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * «Lista de espera cancelada» (ADR-0033, revisión do 2026-11-11): quitarlle á instalación a reserva
 * por internet borra as súas esperas e cada persoa recibe un só correo con todas as súas horas. As
 * reservas quedan. Un bloqueo segue borrando sen correo.
 */
class WaitCancelledMailIT extends AbstractWaitIT {

  @Autowired RecordingMailSender mails;

  List<MimeMessage> cancelledTo(String address) {
    return mails.sentTo(address).stream()
        .filter(
            m -> {
              try {
                return m.getSubject().startsWith("Lista de espera cancelada");
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  Api.Response onlineBooking(Facility f, boolean online) {
    Map<String, Object> details = new HashMap<>();
    details.put("sports", List.of(Map.of("code", "padel")));
    details.put("type", "FIELD");
    details.put("onlineBooking", online);
    details.put("showFreeSlots", !online);
    details.put("covered", false);
    return api()
        .put(facilities(f.slug()) + "/" + f.id() + "/details", details)
        .ifMatch(version(f.slug(), f.id()))
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  @Test
  void turningOffOnlineBookingSendsOneMailPerPersonWithAllTheirHours() throws Exception {
    Facility f = facility("espera-cancelada", true);
    UUID padel = whole(f, "Pádel", Map.of());
    UUID tennis = whole(f, "Tenis", Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    UUID kept = booked(xan, f, padel, at(day, 600));
    booked(xan, f, tennis, at(day, 660));
    String ana = "ana-" + UUID.randomUUID();
    String bea = "bea-" + UUID.randomUUID();
    waiting(person(ana), f, padel, at(day, 600));
    waiting(person(ana), f, tennis, at(day, 660));
    waiting(TestTokens.person(bea, true, "es"), f, padel, at(day, 600));

    Api.Response off = onlineBooking(f, false);
    assertThat(off.status()).as(off.toString()).isEqualTo(200);

    assertThat(waitsIn(f)).isZero();
    assertThat(tallyIn(f)).isZero();
    await(() -> cancelledTo(email(ana)).size() == 1 && cancelledTo(email(bea)).size() == 1);
    settle();
    assertThat(cancelledTo(email(ana))).hasSize(1);
    MimeMessage toAna = cancelledTo(email(ana)).getFirst();
    assertThat(toAna.getSubject()).isEqualTo("Lista de espera cancelada: Pavillón do Alto");
    String text = WaitMailIT.text(toAna);
    assertThat(text)
        .contains("Pavillón do Alto xa non está dispoñible para reservar.")
        .contains("10:00")
        .contains("11:00")
        .contains("Cancelamos as túas esperas, porque esas horas xa non se poderán reservar.")
        .contains("Podes buscar outra instalación");
    assertThat(WaitMailIT.hasAttachment(toAna)).isFalse();
    MimeMessage toBea = cancelledTo(email(bea)).getFirst();
    assertThat(WaitMailIT.text(toBea))
        .contains("Hemos cancelado tu espera, porque esa hora ya no se podrá reservar.");
    // Quedou no rexistro de envíos, coma os demais correos ás esperas.
    assertThat(
            count(
                "select count(*) from notification_delivery where kind = 'WAIT_CANCELLED'"
                    + " and status = 'SENT' and municipality_id = :k",
                f.municipality()))
        .isEqualTo(2);
    // E o rastro no rexistro de actividade, sen persoas.
    assertThat(
            count(
                "select count(*) from audit_event where action = 'booking.waits-removed'"
                    + " and municipality_id = :k",
                f.municipality()))
        .isEqualTo(1);
    // A reserva queda.
    assertThat(
            jdbc.sql("select status from booking where id = :id")
                .param("id", kept)
                .query(String.class)
                .single())
        .isEqualTo("CONFIRMED");
  }

  @Test
  void closingTheFacilityAlsoTellsWhoWasWaiting() throws Exception {
    Facility f = facility("espera-peche", true);
    UUID padel = whole(f, "Pádel", Map.of());
    LocalDate day = tomorrow();
    booked(person(), f, padel, at(day, 600));
    String ana = "ana-" + UUID.randomUUID();
    waiting(person(ana), f, padel, at(day, 600));

    Api.Response closed =
        api()
            .put(
                facilities(f.slug()) + "/" + f.id() + "/status",
                Map.of(
                    "status",
                    "WITHDRAWN",
                    "decisions",
                    List.of(
                        Map.of(
                            "bookingId",
                            jdbc.sql("select id from booking where use_id = :u")
                                .param("u", padel)
                                .query(UUID.class)
                                .single(),
                            "action",
                            "KEEP"))))
            .ifMatch(version(f.slug(), f.id()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(closed.status()).as(closed.toString()).isEqualTo(200);

    await(() -> cancelledTo(email(ana)).size() == 1);
    assertThat(WaitMailIT.text(cancelledTo(email(ana)).getFirst()))
        .contains("Cancelamos a túa espera, porque esa hora xa non se poderá reservar.");
  }

  @Test
  void aBlockStillRemovesWaitsWithoutMail() throws Exception {
    Facility f = facility("espera-bloqueo-sen-correo", true);
    UUID padel = whole(f, "Pádel", Map.of());
    LocalDate day = tomorrow();
    UUID booking = booked(person(), f, padel, at(day, 600));
    String ana = "ana-" + UUID.randomUUID();
    waiting(person(ana), f, padel, at(day, 600));

    Api.Response blocked = blockHours(f, day, 600, 660, List.<Object>of(cancelDecision(booking)));
    assertThat(blocked.status()).as(blocked.toString()).isIn(200, 201);

    assertThat(waitsIn(f)).isZero();
    settle();
    assertThat(cancelledTo(email(ana))).isEmpty();
  }
}
