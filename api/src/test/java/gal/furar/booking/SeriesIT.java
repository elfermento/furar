// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As tempadas das entidades (ADR-0039, puntos 5, 8 e 11): vista previa cos choques, crear só nos
 * días libres e sen pisar nunca unha reserva, a clave de reintento, as sesións na xestión, unha
 * sesión non se move, rematar, o pago (pendente ao nacer se o uso ten prezo) e as listas; centos de
 * sesións nun lote rápido.
 */
class SeriesIT extends AbstractSeasonIT {

  @Test
  void createsOnlyTheFreeDaysAndNeverTreadsOnABooking() {
    Facility f = facility("tempada", true);
    UUID use = whole(f, Map.of());
    UUID club = organization(f, "Club Demo Baloncesto");
    LocalDate monday = nextWorking(1);
    Map<String, Object> schedule =
        schedule(club, f, use, null, List.of(1), 600, 690, monday, monday.plusDays(21));
    // Unha veciña ten o segundo luns ás 10:00.
    UUID taken = booked(person(), f, use, at(monday.plusDays(7), 600));

    JsonNode preview = previewed(f, schedule);
    assertThat(preview.get("sessions").asInt()).isEqualTo(3);
    assertThat(preview.get("conflicts")).hasSize(1);
    JsonNode conflict = preview.get("conflicts").get(0);
    assertThat(conflict.get("kind").asString()).isEqualTo("BOOKING");
    assertThat(conflict.get("bookingId").asString()).isEqualTo(taken.toString());
    assertThat(conflict.get("date").asString()).isEqualTo(monday.plusDays(7).toString());

    // Sen os choques que viu, non crea nada: 409 coa vista previa nova.
    String key = UUID.randomUUID().toString();
    Api.Response stale = create(f, schedule, List.of(), key);
    assertThat(problem(stale)).isEqualTo("409 booking.series-conflicts-changed");
    assertThat(stale.json().get("preview").get("conflicts")).hasSize(1);
    assertThat(
            count(
                "select count(*) from booking_series where municipality_id = :k", f.municipality()))
        .isZero();

    Api.Response created = create(f, schedule, conflictDates(preview), key);
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID seriesId = id(created.json());
    assertThat(created.json().get("sessions").asInt()).isEqualTo(3);
    // Un uso sen prezo: a tempada nace gratis.
    assertThat(created.json().get("paymentStatus").asString()).isEqualTo("FREE");
    assertThat(sessions(seriesId, "CONFIRMED")).isEqualTo(3);
    assertThat(bookingStatus(taken)).isEqualTo("CONFIRMED");

    // A mesma clave e a mesma entrada: a mesma tempada; con outra entrada, 422.
    Api.Response again = create(f, schedule, conflictDates(preview), key);
    assertThat(again.status()).isEqualTo(200);
    assertThat(id(again.json())).isEqualTo(seriesId);
    Map<String, Object> other = new LinkedHashMap<>(schedule);
    other.put("endMinute", 720);
    assertThat(problem(create(f, other, conflictDates(preview), key)))
        .isEqualTo("422 booking.idempotency-key-reused");
    assertThat(problem(create(f, schedule, conflictDates(preview), null)))
        .isEqualTo("400 booking.idempotency-key-missing");

    // A veciñanza xa non pode reservar a hora dunha sesión.
    assertThat(reserve(person(), f, use, at(monday, 600)).status()).isEqualTo(409);

    // Na xestión: a sesión, coa entidade e a súa tempada; non se move.
    UUID session =
        jdbc.sql("select id from booking where series_id = :s order by lower(period) limit 1")
            .param("s", seriesId)
            .query(UUID.class)
            .single();
    JsonNode detail = detail(TestTokens.manager(f.slug()), f.slug(), session);
    assertThat(detail.get("holderName").asString()).isEqualTo("Club Demo Baloncesto");
    assertThat(detail.get("holderEmail").isNull()).isTrue();
    assertThat(detail.get("organization").get("id").asString()).isEqualTo(club.toString());
    assertThat(detail.get("organization").get("series").get("sessions").asInt()).isEqualTo(3);
    assertThat(detail.get("price").get("kind").asString()).isEqualTo("NONE");
    assertThat(detail.get("cancelChannels").toString()).isEqualTo("[\"EMAIL\"]");
    // Recepción vea, pero sen o bloque da tempada.
    JsonNode seen = detail(TestTokens.staff(f.slug()), f.slug(), session);
    assertThat(seen.get("organization").get("series").isNull()).isTrue();
    assertThat(seen.get("organization").get("seriesId").asString()).isEqualTo(seriesId.toString());
    Map<String, Object> move = new LinkedHashMap<>();
    move.put("useId", use);
    move.put("start", at(monday, 720).toString());
    move.put("slots", 1);
    Api.Response moved =
        api()
            .put(bookings(f.slug()) + "/" + session, move)
            .ifMatch(detail.get("version").asLong())
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(problem(moved)).isEqualTo("422 booking.session-not-editable");
    // «Últimas reservas» déixaas fóra; a busca non.
    Api.Response overview =
        api().get(bookings(f.slug()) + "/summary").token(TestTokens.manager(f.slug())).send();
    assertThat(overview.status()).as(overview.toString()).isEqualTo(200);
    assertThat(overview.json().get("latest").toString()).doesNotContain(session.toString());
    Api.Response search =
        api().get(bookings(f.slug()) + "?size=50").token(TestTokens.manager(f.slug())).send();
    assertThat(search.json().get("items").toString()).contains(session.toString());

    // Cancelar unha sesión desde a lista de choques: o de sempre.
    assertThat(cancelByStaff(f, session).status()).isEqualTo(200);
    assertThat(sessions(seriesId, "CONFIRMED")).isEqualTo(2);

    // Rematar desde o terceiro luns: cancela as que quedan desde ese día; repetilo non fai nada.
    Api.Response ended = end(f, seriesId, monday.plusDays(14));
    assertThat(ended.status()).as(ended.toString()).isEqualTo(200);
    assertThat(ended.json().get("endedFrom").asString()).isEqualTo(monday.plusDays(14).toString());
    assertThat(sessions(seriesId, "CONFIRMED")).isZero();
    assertThat(sessions(seriesId, "CANCELLED_BY_MUNICIPALITY")).isEqualTo(3);
    assertThat(end(f, seriesId, monday.plusDays(14)).json().get("cancelled").asInt()).isEqualTo(3);
    assertThat(problem(end(f, seriesId, monday.plusDays(60))))
        .isEqualTo("422 booking.series-end-invalid");
    // A hora queda libre.
    assertThat(reserve(person(), f, use, at(monday.plusDays(21), 600)).status()).isEqualTo(201);

    // O pago da tempada (If-Match).
    long version =
        api()
            .get(series(f.slug()) + "/" + seriesId)
            .token(TestTokens.manager(f.slug()))
            .send()
            .etagVersion();
    Api.Response paid =
        api()
            .put(series(f.slug()) + "/" + seriesId + "/payment", Map.of("status", "PAID"))
            .ifMatch(version)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(paid.status()).as(paid.toString()).isEqualTo(200);
    assertThat(paid.json().get("paymentStatus").asString()).isEqualTo("PAID");
    Api.Response stalePayment =
        api()
            .put(series(f.slug()) + "/" + seriesId + "/payment", Map.of("status", "PENDING"))
            .ifMatch(version)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(stalePayment.status()).isEqualTo(412);

    // As listas: rematada.
    Api.Response active = api().get(series(f.slug())).token(TestTokens.manager(f.slug())).send();
    assertThat(active.json()).isEmpty();
    Api.Response finished =
        api().get(series(f.slug()) + "?status=ENDED").token(TestTokens.manager(f.slug())).send();
    assertThat(finished.json()).hasSize(1);
    assertThat(finished.json().get(0).get("organizationName").asString())
        .isEqualTo("Club Demo Baloncesto");
    // Recepción non xestiona tempadas.
    assertThat(api().get(series(f.slug())).token(TestTokens.staff(f.slug())).send().status())
        .isEqualTo(403);
  }

  @Test
  void aSeasonOfAPaidUseStartsPendingPayment() {
    Facility f = facility("tempada-pago", true);
    UUID use = whole(f, Map.of("price", Map.of("kind", "PAID", "amountCents", 800)));
    UUID club = organization(f, "Club Demo Baloncesto");
    LocalDate monday = nextWorking(1);
    Map<String, Object> schedule =
        schedule(club, f, use, null, List.of(1), 600, 690, monday, monday.plusDays(7));
    Api.Response created =
        create(f, schedule, conflictDates(previewed(f, schedule)), UUID.randomUUID().toString());
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.json().get("paymentStatus").asString()).isEqualTo("PENDING");
  }

  @Test
  void aPersonAndASeasonAtTheSameTimeNeverOverlap() throws Exception {
    Facility f = facility("tempada-carreira", true);
    UUID use = whole(f, Map.of());
    UUID club = organization(f, "Club Carreira");
    LocalDate tuesday = nextWorking(2);
    Map<String, Object> schedule =
        schedule(club, f, use, null, List.of(2), 600, 660, tuesday, tuesday);
    List<String> skip = conflictDates(previewed(f, schedule));
    String person = person();

    List<Callable<Api.Response>> calls =
        List.of(
            () -> reserve(person, f, use, at(tuesday, 600)),
            () -> create(f, schedule, skip, UUID.randomUUID().toString()));
    List<Api.Response> responses = together(calls);

    int active =
        count("select count(*) from booking_occupancy where active and facility_id = :k", f.id());
    assertThat(active).as(responses.toString()).isEqualTo(1);
    if (responses.get(0).status() == 201) {
      assertThat(responses.get(1).status()).as(responses.toString()).isEqualTo(409);
      assertThat(problem(responses.get(1))).isEqualTo("409 booking.series-conflicts-changed");
    } else {
      assertThat(responses.get(1).status()).isEqualTo(201);
      assertThat(responses.get(0).status()).isEqualTo(409);
    }
  }

  @Test
  void aYearOfSessionsIsWrittenInOneQuickBatch() {
    Facility f = facility("tempada-ano", true);
    UUID use = whole(f, Map.of());
    UUID club = organization(f, "Club Ano");
    LocalDate first = LocalDate.now(ZONE).plusDays(2);
    Map<String, Object> schedule =
        schedule(
            club, f, use, null, List.of(1, 2, 3, 4, 5, 6, 7), 600, 690, first, first.plusDays(365));
    List<String> skip = conflictDates(previewed(f, schedule));

    Instant started = Instant.now();
    Api.Response created = create(f, schedule, skip, UUID.randomUUID().toString());
    Duration took = Duration.between(started, Instant.now());

    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID seriesId = id(created.json());
    int expected = created.json().get("sessions").asInt();
    assertThat(expected).isGreaterThan(300);
    assertThat(sessions(seriesId, "CONFIRMED")).isEqualTo(expected);
    assertThat(
            count(
                "select count(*) from booking_occupancy o join booking b on b.id = o.booking_id"
                    + " where o.active and b.series_id = :k",
                seriesId))
        .isEqualTo(expected);
    assertThat(
            count(
                "select count(*) from booking_event e join booking b on b.id = e.booking_id"
                    + " where e.kind = 'CREATED' and b.series_id = :k",
                seriesId))
        .isEqualTo(expected);
    assertThat(took).as("%s sesións: %s", expected, took).isLessThan(Duration.ofSeconds(1));

    // Rematala tamén vai nun lote.
    Instant ending = Instant.now();
    Api.Response ended = end(f, seriesId, first.plusDays(1));
    assertThat(ended.status()).as(ended.toString()).isEqualTo(200);
    assertThat(Duration.between(ending, Instant.now())).isLessThan(Duration.ofSeconds(1));
    assertThat(sessions(seriesId, "CONFIRMED")).isEqualTo(1);
  }

  @Test
  void validatesTheShapeAndTheUse() {
    Facility f = facility("tempada-forma", true);
    UUID use = whole(f, Map.of());
    UUID seats = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 10)));
    UUID club = organization(f, "Club Forma");
    LocalDate monday = nextWorking(1);

    assertThat(
            problem(preview(f, schedule(club, f, use, null, List.of(), 600, 690, monday, monday))))
        .isEqualTo("422 booking.series-weekdays-required");
    assertThat(
            problem(preview(f, schedule(club, f, use, null, List.of(1), 605, 690, monday, monday))))
        .isEqualTo("422 booking.series-time-invalid");
    assertThat(
            problem(
                preview(
                    f,
                    schedule(
                        club, f, use, null, List.of(1), 600, 690, monday, monday.plusDays(400)))))
        .isEqualTo("422 booking.series-range-invalid");
    assertThat(
            problem(
                preview(f, schedule(club, f, seats, null, List.of(1), 600, 690, monday, monday))))
        .isEqualTo("422 booking.organization-seats-use");
    assertThat(
            problem(
                preview(
                    f,
                    schedule(
                        UUID.randomUUID(), f, use, null, List.of(1), 600, 690, monday, monday))))
        .isEqualTo("404 booking.organization-not-found");
  }

  private String bookingStatus(UUID booking) {
    return jdbc.sql("select status from booking where id = :id")
        .param("id", booking)
        .query(String.class)
        .single();
  }
}
