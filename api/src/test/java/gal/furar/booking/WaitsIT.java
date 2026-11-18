// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Esperar por unha hora ocupada, deixar de esperar e ver as esperas (ADR-0033, puntos 4 a 6): a
 * orde das comprobacións, o límite de tres na base tamén con peticións á vez, e que cada conta só
 * ve e borra as súas.
 */
class WaitsIT extends AbstractWaitIT {

  @Test
  void aPersonWaitsForABusyHourSeesItAndStopsWaiting() {
    Facility f = facility("espera", true);
    UUID use = whole(f, "Pádel", Map.of());
    Instant ten = at(tomorrow(), 600);
    booked(person(), f, use, ten);
    String sub = "ana-" + UUID.randomUUID();
    String ana = person(sub);

    Api.Response joined = join(ana, f, use, ten);

    assertThat(joined.status()).as(joined.toString()).isEqualTo(201);
    JsonNode wait = joined.json();
    assertThat(wait.get("facilityId").asString()).isEqualTo(f.id().toString());
    assertThat(wait.get("facilityName").asString()).isEqualTo("Pavillón do Alto");
    assertThat(wait.get("useId").asString()).isEqualTo(use.toString());
    assertThat(wait.get("useName").asString()).isEqualTo("Pádel");
    assertThat(Instant.parse(wait.get("start").asString())).isEqualTo(ten);
    assertThat(Instant.parse(wait.get("end").asString())).isEqualTo(ten.plusSeconds(3600));
    assertThat(wait.has("cover")).isTrue();
    assertThat(
            jdbc.sql(
                    "select account_subject || ' ' || email_key || ' ' || language || ' ' || ordinal"
                        + " from booking_wait where id = :id")
                .param("id", id(wait))
                .query(String.class)
                .single())
        .isEqualTo(sub + " " + email(sub) + " gl 1");

    // Repetir non cambia nada: 200 coa mesma espera.
    Api.Response again = join(ana, f, use, ten);
    assertThat(again.status()).as(again.toString()).isEqualTo(200);
    assertThat(id(again.json())).isEqualTo(id(wait));
    assertThat(myWaits(ana, f)).containsExactly(id(wait));
    // Outra conta non a ve nin a borra.
    String bea = person();
    assertThat(myWaits(bea, f)).isEmpty();
    assertThat(problem(leave(bea, f, id(wait)))).isEqualTo("404 booking.wait-not-found");

    assertThat(leave(ana, f, id(wait)).status()).isEqualTo(204);
    assertThat(myWaits(ana, f)).isEmpty();
    assertThat(problem(leave(ana, f, id(wait)))).isEqualTo("404 booking.wait-not-found");
    assertThat(tallyIn(f)).as("deixar de esperar non conta").isZero();
  }

  @Test
  void theChecksComeInOrder() {
    Facility f = facility("espera-erros", true);
    UUID use = whole(f, Map.of("rules", Map.of("minAdvanceHours", 1, "maxAdvanceDays", 5)));
    LocalDate day = tomorrow();
    Instant ten = at(day, 600);
    String ana = person();
    UUID own = booked(ana, f, use, ten);
    String bea = person();

    // Sen sesión, sen correo verificado e noutra instalación que non existe.
    assertThat(join(null, f, use, ten).status()).isEqualTo(401);
    assertThat(problem(join(TestTokens.person("p-" + UUID.randomUUID(), false), f, use, ten)))
        .isEqualTo("403 auth.email-not-verified");
    assertThat(
            api()
                .post(
                    waits(f.slug()),
                    Map.of("facilityId", UUID.randomUUID(), "useId", use, "start", ten.toString()))
                .token(bea)
                .send()
                .status())
        .isEqualTo(404);
    assertThat(api().post(waits(f.slug()), Map.of("facilityId", f.id())).token(bea).send().status())
        .isEqualTo(400);
    // A hora: fóra da grella, xa pasada, demasiado lonxe e libre.
    assertThat(problem(join(bea, f, use, at(day, 615)))).isEqualTo("422 booking.slot-invalid");
    assertThat(problem(join(bea, f, use, at(day, 900)))).isEqualTo("422 booking.slot-invalid");
    assertThat(problem(join(bea, f, use, at(day.plusDays(30), 600))))
        .isEqualTo("422 booking.too-far");
    assertThat(problem(join(bea, f, use, at(day, 660)))).isEqualTo("409 booking.wait-slot-free");
    // Quen xa ten reserva dese uso nesa franxa non espera por ela.
    assertThat(problem(join(ana, f, use, ten))).isEqualTo("409 booking.wait-own-booking");
    assertThat(waitsIn(f)).isZero();
    assertThat(own).isNotNull();
  }

  @Test
  void aWorkAccountAndABlockedPersonCannotWait() {
    Facility f = facility("espera-quen", true);
    UUID use = whole(f, Map.of());
    Instant ten = at(tomorrow(), 600);
    booked(person(), f, use, ten);

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
    assertThat(problem(join(person(worker), f, use, ten))).isEqualTo("403 booking.staff-account");

    // Bloqueada por faltas: non pode esperar; a espera que xa tiña queda.
    String sub = "dora-" + UUID.randomUUID();
    UUID before = waiting(person(sub), f, use, ten);
    blockByNoShows(f, email(sub));
    Instant eleven = at(tomorrow(), 660);
    booked(person(), f, use, eleven);
    Api.Response refused = join(person(sub), f, use, eleven);
    assertThat(problem(refused)).isEqualTo("403 booking.blocked-no-show");
    assertThat(refused.json().get("params").get("faults").asInt()).isZero();
    assertThat(myWaits(person(sub), f)).containsExactly(before);
  }

  @Test
  void theFourthWaitIsRefusedAndAPastOneLeavesRoom() {
    Facility f = facility("espera-limite", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String other = person();
    List<Instant> hours = List.of(at(day, 540), at(day, 600), at(day, 660), at(day, 720));
    hours.forEach(h -> booked(other, f, use, h));
    String sub = "ana-" + UUID.randomUUID();
    String ana = person(sub);
    List<UUID> waits = new ArrayList<>();
    hours.subList(0, 3).forEach(h -> waits.add(waiting(ana, f, use, h)));

    Api.Response fourth = join(ana, f, use, hours.get(3));

    assertThat(problem(fourth)).isEqualTo("422 booking.wait-limit");
    assertThat(fourth.json().get("params").get("max").asInt()).isEqualTo(3);
    assertThat(myWaits(ana, f)).containsExactlyElementsOf(waits);
    // Deixar unha deixa sitio; e unha xa pasada tampouco o ocupa: vence ao crear a seguinte.
    assertThat(leave(ana, f, waits.get(0)).status()).isEqualTo(204);
    waiting(ana, f, use, hours.get(3));
    jdbc.sql(
            "update booking_wait set slot_start = :past, slot_end = :past + interval '1 hour'"
                + " where id = :id")
        .param("past", OffsetDateTime.now(ZoneOffset.UTC).minusHours(3))
        .param("id", waits.get(1))
        .update();
    assertThat(myWaits(ana, f)).as("a vencida non se lista").hasSize(2);
    booked(other, f, use, at(day, 780));
    waiting(ana, f, use, at(day, 780));
    assertThat(myWaits(ana, f)).hasSize(3);
    assertThat(tallyIn(f)).as("a vencida pasa ao reconto").isEqualTo(1);
  }

  /** Tres «esperar» á vez da mesma conta sobre instalacións distintas: entran as tres. */
  @Test
  void threeAtOnceOverDifferentFacilitiesAllEnterAndAFourthIsRefused() throws Exception {
    Facility first = facility("espera-vez", true);
    List<Facility> all = new ArrayList<>(List.of(first));
    for (int i = 2; i <= 4; i++) {
      all.add(facility(first.slug(), first.municipality(), "Pavillón " + i, true));
    }
    Instant ten = at(tomorrow(), 600);
    String other = person();
    List<UUID> uses = new ArrayList<>();
    for (Facility f : all) {
      UUID use = whole(f, Map.of());
      uses.add(use);
      booked(other, f, use, ten);
    }
    String ana = person();

    List<Callable<Api.Response>> three = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      int n = i;
      three.add(() -> join(ana, all.get(n), uses.get(n), ten));
    }
    assertThat(byStatus(together(three))).isEqualTo(Map.of(201, 3L));
    assertThat(myWaits(ana, first)).hasSize(3);
    assertThat(problem(join(ana, all.get(3), uses.get(3), ten)))
        .isEqualTo("422 booking.wait-limit");

    // Catro á vez desde cero: tres entran e unha dá 422, nunca un erro falso.
    String bea = person();
    List<Callable<Api.Response>> four = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      int n = i;
      four.add(() -> join(bea, all.get(n), uses.get(n), ten));
    }
    List<Api.Response> responses = together(four);
    assertThat(byStatus(responses)).isEqualTo(Map.of(201, 3L, 422, 1L));
    assertThat(responses.stream().filter(r -> r.status() == 422).map(AbstractWaitIT::problem))
        .containsExactly("422 booking.wait-limit");
    assertThat(myWaits(bea, first)).hasSize(3);
  }

  /** Dúas á vez pola mesma hora: unha soa espera. */
  @Test
  void twoAtOnceForTheSameHourMakeOneWait() throws Exception {
    Facility f = facility("espera-dobre", true);
    UUID use = whole(f, Map.of());
    Instant ten = at(tomorrow(), 600);
    booked(person(), f, use, ten);
    String ana = person();

    List<Api.Response> responses =
        together(List.of(() -> join(ana, f, use, ten), () -> join(ana, f, use, ten)));

    assertThat(byStatus(responses)).isEqualTo(Map.of(201, 1L, 200, 1L));
    assertThat(id(responses.get(0).json())).isEqualTo(id(responses.get(1).json()));
    assertThat(waitsIn(f)).isEqualTo(1);
  }

  /** Se a persoa cambia o correo da conta, as súas esperas pasan ao novo cando volve entrar. */
  @Test
  void theVerifiedEmailOfTheTokenReplacesTheOldOne() {
    Facility f = facility("espera-correo", true);
    UUID use = whole(f, Map.of());
    Instant ten = at(tomorrow(), 600);
    booked(person(), f, use, ten);
    String sub = "ana-" + UUID.randomUUID();
    UUID wait = waiting(person(sub), f, use, ten);
    jdbc.sql("update booking_wait set email_key = 'vello@example.org' where id = :id")
        .param("id", wait)
        .update();

    assertThat(myWaits(person(sub), f)).containsExactly(wait);

    assertThat(
            jdbc.sql("select email_key from booking_wait where id = :id")
                .param("id", wait)
                .query(String.class)
                .single())
        .isEqualTo(email(sub));
  }
}
