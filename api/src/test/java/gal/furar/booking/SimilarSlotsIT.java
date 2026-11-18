// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.StatementCounter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * «Ocos parecidos» (ADR-0033, punto 9): as catro regras sobre datos reais, sen sesión e con ela (as
 * normas da persoa), os erros e un número fixo de lecturas sexan cantas sexan as instalacións.
 */
class SimilarSlotsIT extends AbstractWaitIT {

  String url(Facility f, boolean me, UUID use, Instant start, String more) {
    return "/api/v1/municipalities/"
        + f.slug()
        + (me ? "/me" : "/public")
        + "/facilities/"
        + f.id()
        + "/similar-slots?useId="
        + use
        + "&start="
        + start
        + more;
  }

  Api.Response similar(String token, Facility f, UUID use, Instant start) {
    Api.Call call = api().get(url(f, token != null, use, start, ""));
    return token == null ? call.send() : call.token(token).send();
  }

  /** «REGRA instalación día minuto» de cada fila. */
  List<String> rows(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    List<String> rows = new ArrayList<>();
    response
        .json()
        .get("items")
        .forEach(
            i ->
                rows.add(
                    i.get("reason").asString()
                        + " "
                        + i.get("facilityName").asString()
                        + " "
                        + i.get("date").asString()
                        + " "
                        + i.get("startMinute").asInt()));
    return rows;
  }

  @Test
  void theFourRulesWithoutSessionAndWithThePersonsRules() {
    Facility f = facility("parecidos", true);
    UUID padel = whole(f, "Pádel", Map.of("rules", Map.of("capCount", 1, "capPeriod", "DAY")));
    Facility g = facility(f.slug(), f.municipality(), "Pista da Gándara", true);
    UUID same = whole(g, "padel", Map.of());
    Facility h = facility(f.slug(), f.municipality(), "Pista do Río", true);
    // Mesmo nome pero outra duración: non é «a mesma hora» e non conta.
    id(use(h, "Pádel", "WHOLE", Map.of("slotMinutes", 30)));
    LocalDate day = tomorrow();
    String xan = person();
    booked(xan, f, padel, at(day, 600));
    booked(person(), f, padel, at(day, 660));
    Instant ten = at(day, 600);

    Api.Response open = similar(null, f, padel, ten);

    assertThat(rows(open))
        .containsExactly(
            "SAME_FACILITY_NEAR Pavillón do Alto " + day + " 540",
            "OTHER_FACILITY_SAME_TIME Pista da Gándara " + day + " 600",
            "LATER_SAME_DAY Pavillón do Alto " + day + " 720",
            "NEXT_WEEK Pavillón do Alto " + day.plusDays(7) + " 600");
    JsonNode other = open.json().get("items").get(1);
    assertThat(other.get("facilityId").asString()).isEqualTo(g.id().toString());
    assertThat(other.get("useId").asString()).isEqualTo(same.toString());
    assertThat(other.get("facilityAddress").asString()).isEqualTo("Rúa do Alto, 12");
    assertThat(Instant.parse(other.get("start").asString())).isEqualTo(ten);
    assertThat(Instant.parse(other.get("end").asString())).isEqualTo(ten.plusSeconds(3600));
    assertThat(open.json().get("items").get(0).hasNonNull("facilityAddress")).isFalse();

    // Coa sesión de quen xa chegou ao límite dese día nese uso: aquí non pode, na outra si.
    assertThat(rows(similar(xan, f, padel, ten)))
        .containsExactly(
            "OTHER_FACILITY_SAME_TIME Pista da Gándara " + day + " 600",
            "LATER_SAME_DAY Pista da Gándara " + day + " 660",
            "NEXT_WEEK Pavillón do Alto " + day.plusDays(7) + " 600");
    // E quen non ten nada ve o mesmo ca sen sesión.
    assertThat(rows(similar(person(), f, padel, ten))).hasSize(4);
    // Con dúas franxas seguidas: ás 9 só cabe unha (as 10 están collidas).
    assertThat(rows(api().get(url(f, false, padel, ten, "&slots=2")).send()))
        .containsExactly(
            "OTHER_FACILITY_SAME_TIME Pista da Gándara " + day + " 600",
            "LATER_SAME_DAY Pavillón do Alto " + day + " 720",
            "NEXT_WEEK Pavillón do Alto " + day.plusDays(7) + " 600");
  }

  /** Con franxas de 90 minutos, a primeira fila é a franxa anterior ou a seguinte (2026-11-11). */
  @Test
  void withNinetyMinuteSlotsTheFirstRowIsThePreviousOrTheNextSlot() {
    Facility f = facility("parecidos-noventa", true);
    // Aberta de 9:00 a 14:00: franxas ás 9:00, 10:30 e 12:00.
    UUID padel = id(use(f, "Partido", "WHOLE", Map.of("slotMinutes", 90)));
    LocalDate day = tomorrow();
    Instant halfPastTen = at(day, 630);
    String xan = person();
    booked(xan, f, padel, halfPastTen);

    // As dúas veciñas libres: a anterior; «máis tarde», a seguinte.
    assertThat(rows(similar(null, f, padel, halfPastTen)))
        .containsExactly(
            "SAME_FACILITY_NEAR Pavillón do Alto " + day + " 540",
            "LATER_SAME_DAY Pavillón do Alto " + day + " 720",
            "NEXT_WEEK Pavillón do Alto " + day.plusDays(7) + " 630");
    // Coa anterior collida, a seguinte.
    booked(person(), f, padel, at(day, 540));
    assertThat(rows(similar(null, f, padel, halfPastTen)))
        .containsExactly(
            "SAME_FACILITY_NEAR Pavillón do Alto " + day + " 720",
            "NEXT_WEEK Pavillón do Alto " + day.plusDays(7) + " 630");
  }

  @Test
  void theErrorsAndTheEmptyAnswers() {
    Facility f = facility("parecidos-erros", true);
    UUID use = whole(f, Map.of());
    Facility g = facility(f.slug(), f.municipality(), "Pista da Gándara", true);
    UUID elsewhere = whole(g, Map.of());
    LocalDate day = tomorrow();
    Instant ten = at(day, 600);
    booked(person(), f, use, ten);

    assertThat(similar(null, f, elsewhere, ten).status()).isEqualTo(404);
    assertThat(
            similar(null, new Facility(f.slug(), f.municipality(), UUID.randomUUID()), use, ten)
                .status())
        .isEqualTo(404);
    assertThat(problem(similar(null, f, use, at(day, 615)))).isEqualTo("422 booking.slot-invalid");
    assertThat(api().get(url(f, false, use, ten, "&slots=17")).send().status()).isEqualTo(400);
    assertThat(api().get(url(f, false, use, ten, "&slots=0")).send().status()).isEqualTo(400);
    // Unha hora libre ou xa pasada: 200 e nada.
    assertThat(rows(similar(null, f, use, at(day, 660)))).isEmpty();
    assertThat(rows(similar(null, f, use, at(LocalDate.now(ZONE).minusDays(1), 600)))).isEmpty();
    // A ruta coa sesión pide sesión; a pública, non.
    assertThat(api().get(url(f, true, use, ten, "")).send().status()).isEqualTo(401);
    assertThat(rows(similar(null, f, use, ten))).isNotEmpty();
  }

  /** Quen non pode reservar por internet non recibe ocos que non podería coller. */
  @Test
  void aWorkAccountAndABlockedPersonGetNothing() {
    Facility f = facility("parecidos-quen", true);
    UUID use = whole(f, Map.of());
    Instant ten = at(tomorrow(), 600);
    booked(person(), f, use, ten);
    String blocked = "dora-" + UUID.randomUUID();
    assertThat(rows(similar(person(blocked), f, use, ten))).isNotEmpty();
    blockByNoShows(f, email(blocked));
    String worker = "persoal-" + UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at, version)
            values (:id, :m, :email, 'Persoa de proba', :subject, 'STAFF', 'ACTIVE', 'gl',
                    'sub-semente', :now, :now, :now, :now, 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", f.municipality())
        .param("email", email(worker))
        .param("subject", worker)
        .param("now", now)
        .update();

    assertThat(rows(similar(person(blocked), f, use, ten))).isEmpty();
    assertThat(rows(similar(person(worker), f, use, ten))).isEmpty();
  }

  /** As lecturas non medran coas instalacións: as mesmas con 2 ca con 12 co mesmo uso. */
  @Test
  void theNumberOfStatementsDoesNotGrowWithTheFacilities() {
    Facility f = facility("parecidos-custo", true);
    UUID use = whole(f, "Pádel", Map.of());
    Instant ten = at(tomorrow(), 600);
    booked(person(), f, use, ten);
    whole(facility(f.slug(), f.municipality(), "Pista 01", true), "Pádel", Map.of());
    String ana = person();

    long few = statements(() -> assertThat(rows(similar(ana, f, use, ten))).hasSize(4));
    for (int i = 2; i <= 11; i++) {
      whole(
          facility(f.slug(), f.municipality(), "Pista " + String.format("%02d", i), true),
          "Pádel",
          Map.of());
    }
    long many = statements(() -> assertThat(rows(similar(ana, f, use, ten))).hasSize(4));

    assertThat(many).isEqualTo(few);
  }

  static long statements(Runnable request) {
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      request.run();
      return recording.count(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith("select"));
    }
  }
}
