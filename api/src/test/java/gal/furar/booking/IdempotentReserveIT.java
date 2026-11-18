// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.IdempotencyKeyRetentionService;
import gal.furar.testsupport.Api;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Reservar é idempotente (ADR-0025, punto 19; ADR-0009): a mesma {@code Idempotency-Key} coa mesma
 * petición devolve a mesma reserva sen crear outra (tamén á vez e nun uso por partes sen parte
 * elixida); con outra petición, 422; sen a cabeceira ou mal formada, 400; a doutra persoa é outra
 * clave; e a limpeza horaria borra as de máis de 24 horas.
 */
class IdempotentReserveIT extends AbstractBookingIT {

  @Autowired IdempotencyKeyRetentionService retention;

  int bookings(Facility f) {
    return count("select count(*) from booking where facility_id = :k", f.id());
  }

  int keys(Facility f) {
    return count(
        "select count(*) from booking_idempotency_key where booking_id in"
            + " (select id from booking where facility_id = :k)",
        f.id());
  }

  @Test
  void theSameKeyAndBodyReturnTheSameBookingWithTheSameResponse() {
    Facility f = facility("idem-mesma", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    String key = UUID.randomUUID().toString();
    Instant nine = at(tomorrow(), OPENS);

    Api.Response first = reserve(ana, f, use, nine, 1, null, key);
    Api.Response again = reserve(ana, f, use, nine, 1, null, key);

    assertThat(first.status()).as(first.toString()).isEqualTo(201);
    assertThat(again.status()).as(again.toString()).isEqualTo(201);
    assertThat(id(again.json())).isEqualTo(id(first.json()));
    assertThat(code(again)).isEqualTo(code(first));
    assertThat(again.header("Location")).isEqualTo(first.header("Location"));
    assertThat(again.header("ETag")).isEqualTo(first.header("ETag")).isEqualTo("\"0\"");
    assertThat(bookings(f)).isEqualTo(1);
    assertThat(keys(f)).isEqualTo(1);
    assertThat(
            count(
                "select count(*) from booking_event where kind = 'CREATED' and booking_id = :k",
                id(first.json())))
        .isEqualTo(1);
    // Entre comiñas (cadea dun campo estruturado, como no borrador) é a mesma clave.
    Api.Response quoted = reserve(ana, f, use, nine, 1, null, "\"" + key + "\"");
    assertThat(quoted.status()).as(quoted.toString()).isEqualTo(201);
    assertThat(id(quoted.json())).isEqualTo(id(first.json()));
  }

  @Test
  void theReplayShowsTheBookingAsItIsNowWithItsCurrentVersion() {
    Facility f = facility("idem-agora", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    String key = UUID.randomUUID().toString();
    Instant nine = at(tomorrow(), OPENS);
    UUID id = id(reserve(ana, f, use, nine, 1, null, key).json());
    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + id + "/cancel", null).ifMatch(0).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);

    Api.Response again = reserve(ana, f, use, nine, 1, null, key);

    assertThat(again.status()).as(again.toString()).isEqualTo(201);
    assertThat(id(again.json())).isEqualTo(id);
    assertThat(again.json().get("status").asString()).isEqualTo("CANCELLED_BY_PERSON");
    assertThat(again.header("ETag")).isEqualTo("\"1\"");
    assertThat(bookings(f)).as("nin sequera co oco xa libre").isEqualTo(1);
  }

  @Test
  void theSameKeyWithAnotherBodyIsRefusedWith422() {
    Facility f = facility("idem-outra", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    String key = UUID.randomUUID().toString();
    assertThat(reserve(ana, f, use, at(tomorrow(), OPENS), 1, null, key).status()).isEqualTo(201);

    Api.Response other = reserve(ana, f, use, at(tomorrow(), 600), 1, null, key);
    Api.Response longer = reserve(ana, f, use, at(tomorrow(), OPENS), 2, null, key);

    for (Api.Response r : List.of(other, longer)) {
      assertThat(r.status()).as(r.toString()).isEqualTo(422);
      assertThat(r.contentType()).startsWith("application/problem+json");
      assertThat(code(r)).isEqualTo("booking.idempotency-key-reused");
    }
    assertThat(bookings(f)).isEqualTo(1);
  }

  @Test
  void withoutTheHeaderOrMalformedIs400AndNothingIsBooked() {
    Facility f = facility("idem-sen", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    Instant nine = at(tomorrow(), OPENS);

    Api.Response missing = reserve(ana, f, use, nine, 1, null, null);
    Api.Response blank = reserve(ana, f, use, nine, 1, null, "   ");
    Api.Response tooLong = reserve(ana, f, use, nine, 1, null, "k".repeat(256));
    Api.Response spaces = reserve(ana, f, use, nine, 1, null, "unha clave");

    assertThat(missing.status()).as(missing.toString()).isEqualTo(400);
    assertThat(code(missing)).isEqualTo("booking.idempotency-key-missing");
    assertThat(blank.status()).isEqualTo(400);
    assertThat(code(blank)).isEqualTo("booking.idempotency-key-missing");
    for (Api.Response r : List.of(tooLong, spaces)) {
      assertThat(r.status()).as(r.toString()).isEqualTo(400);
      assertThat(code(r)).isEqualTo("booking.idempotency-key-invalid");
      assertThat(r.json().get("params").get("max").asInt()).isEqualTo(255);
    }
    assertThat(bookings(f)).isZero();
  }

  @Test
  void tenSimultaneousRequestsWithTheSameKeyMakeOneBooking() throws Exception {
    Facility f = facility("idem-vez", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    String key = UUID.randomUUID().toString();
    Instant nine = at(tomorrow(), OPENS);
    List<Callable<Api.Response>> calls = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      calls.add(() -> reserve(ana, f, use, nine, 1, null, key));
    }

    List<Api.Response> responses = together(calls);

    assertThat(byStatus(responses)).as(responses.toString()).containsOnlyKeys(201);
    assertThat(responses.stream().map(r -> id(r.json())).distinct()).hasSize(1);
    assertThat(bookings(f)).isEqualTo(1);
    assertThat(keys(f)).isEqualTo(1);
  }

  /**
   * O fallo que motivou a clave: nun uso por partes sen parte elixida, un reintento collía a
   * seguinte parte libre e creaba unha segunda reserva.
   */
  @Test
  void aRetryWithoutAChosenPartDoesNotTakeTheNextPart() {
    Facility f = facility("idem-partes", true);
    JsonNode created =
        use(
            f,
            "Medio campo",
            "PARTS",
            Map.of("parts", List.of(Map.of("name", "Metade norte"), Map.of("name", "Metade sur"))));
    UUID use = id(created);
    String ana = person();
    String key = UUID.randomUUID().toString();
    Instant nine = at(tomorrow(), OPENS);

    Api.Response first = reserve(ana, f, use, nine, 1, null, key);
    Api.Response again = reserve(ana, f, use, nine, 1, null, key);

    assertThat(first.status()).as(first.toString()).isEqualTo(201);
    assertThat(again.status()).as(again.toString()).isEqualTo(201);
    assertThat(id(again.json())).isEqualTo(id(first.json()));
    assertThat(again.json().get("partName").asString()).isEqualTo("Metade norte");
    assertThat(bookings(f)).isEqualTo(1);
    // A outra metade segue libre para quen veña.
    Api.Response someoneElse = reserve(person(), f, use, nine);
    assertThat(someoneElse.status()).as(someoneElse.toString()).isEqualTo(201);
    assertThat(someoneElse.json().get("partName").asString()).isEqualTo("Metade sur");
  }

  @Test
  void theSameKeyOfAnotherPersonIsAnotherKey() {
    Facility f = facility("idem-persoa", true);
    UUID use = whole(f, Map.of());
    String key = UUID.randomUUID().toString();
    UUID anas = id(reserve(person(), f, use, at(tomorrow(), OPENS), 1, null, key).json());

    Api.Response brais = reserve(person(), f, use, at(tomorrow(), 600), 1, null, key);
    Api.Response braisOnAnasSlot = reserve(person(), f, use, at(tomorrow(), OPENS), 1, null, key);

    assertThat(brais.status()).as(brais.toString()).isEqualTo(201);
    assertThat(id(brais.json())).isNotEqualTo(anas);
    assertThat(braisOnAnasSlot.status())
        .as("non ve a reserva de Ana: o oco está collido")
        .isEqualTo(409);
    assertThat(code(braisOnAnasSlot)).isEqualTo("booking.slot-taken");
  }

  @Test
  void aFailedAttemptKeepsNoKeySoTheRetryIsEvaluatedAgain() {
    Facility f = facility("idem-falla", true);
    UUID use = whole(f, Map.of());
    Instant nine = at(tomorrow(), OPENS);
    String brais = person();
    UUID braisBooking = id(reserve(brais, f, use, nine).json());
    String ana = person();
    String key = UUID.randomUUID().toString();

    Api.Response taken = reserve(ana, f, use, nine, 1, null, key);
    assertThat(code(taken)).isEqualTo("booking.slot-taken");
    api()
        .post(mine(f.slug()) + "/" + braisBooking + "/cancel", null)
        .ifMatch(0)
        .token(brais)
        .send();
    Api.Response retried = reserve(ana, f, use, nine, 1, null, key);

    assertThat(retried.status()).as(retried.toString()).isEqualTo(201);
    assertThat(
            jdbc.sql("select booking_id from booking_idempotency_key where idempotency_key = :k")
                .param("k", key)
                .query(UUID.class)
                .list())
        .containsExactly(id(retried.json()));
  }

  @Test
  void theHourlyCleanupForgetsKeysOlderThan24Hours() {
    Facility f = facility("idem-limpa", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    String old = UUID.randomUUID().toString();
    String fresh = UUID.randomUUID().toString();
    UUID oldBooking = id(reserve(ana, f, use, at(tomorrow(), OPENS), 1, null, old).json());
    reserve(ana, f, use, at(tomorrow(), 600), 1, null, fresh);
    jdbc.sql(
            """
            update booking_idempotency_key set created_at = created_at - interval '25 hours'
            where booking_id = :b
            """)
        .param("b", oldBooking)
        .update();

    int deleted = retention.apply();

    assertThat(deleted).isGreaterThanOrEqualTo(1);
    assertThat(keys(f)).isEqualTo(1);
    assertThat(
            count("select count(*) from booking_idempotency_key where booking_id = :k", oldBooking))
        .isZero();
    assertThat(bookings(f)).as("a reserva queda; só se esquece a clave").isEqualTo(2);
  }
}
