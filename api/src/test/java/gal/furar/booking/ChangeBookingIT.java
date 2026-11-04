// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import tools.jackson.databind.JsonNode;

/**
 * Cambiar e cancelar de punta a punta (plan do dominio 2, D2-06; ADR-0025, puntos 8 e 12; P1, P2 e
 * P3): o cambio é unha transacción sobre a mesma reserva que non conta a vella; se falla, a vella
 * queda exactamente como estaba (fila, ocupación, prazas e versión). Cancelar pagada deixa «a
 * devolver»; dúas veces, 409; e os dous levan {@code If-Match}.
 */
class ChangeBookingIT extends AbstractBookingIT {

  Api.Response change(
      String token, Facility f, UUID booking, long version, UUID use, Instant start, int slots) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", slots);
    return api()
        .put(mine(f.slug()) + "/" + booking + "/slot", body)
        .ifMatch(version)
        .token(token)
        .send();
  }

  Api.Response change(String token, Facility f, UUID booking, long version, UUID use, Instant to) {
    return change(token, f, booking, version, use, to, 1);
  }

  Api.Response cancel(String token, Facility f, UUID booking, long version) {
    return api()
        .post(mine(f.slug()) + "/" + booking + "/cancel", null)
        .ifMatch(version)
        .token(token)
        .send();
  }

  /** Todo o que a reserva ten na base, para comparar antes e despois dun cambio que falla. */
  Map<String, Object> stored(UUID booking) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.putAll(
        jdbc.sql(
                """
                select use_id, part_id, lower(period) as start_at, upper(period) as end_at, slots,
                       status, payment_status, amount_cents, cancel_until, updated_at, version
                from booking where id = :k
                """)
            .param("k", booking)
            .query()
            .singleRow());
    state.put(
        "occupancy",
        jdbc.sql(
                """
                select lower(period) as start_at, active from booking_occupancy
                where booking_id = :k order by lower(period), active
                """)
            .param("k", booking)
            .query()
            .listOfRows());
    state.put(
        "events",
        jdbc.sql("select kind from booking_event where booking_id = :k order by at, kind")
            .param("k", booking)
            .query(String.class)
            .list());
    return state;
  }

  List<Integer> seatsTaken(UUID use) {
    return jdbc.sql("select taken from use_slot where use_id = :k order by slot_start")
        .param("k", use)
        .query(Integer.class)
        .list();
  }

  /** Os eventos públicos de reservas que se publican mentres corre {@code action}. */
  List<Object> events(Runnable action) {
    List<Object> seen = new CopyOnWriteArrayList<>();
    ApplicationListener<ApplicationEvent> listener =
        e -> {
          if (e instanceof PayloadApplicationEvent<?> p
              && (p.getPayload() instanceof BookingChanged
                  || p.getPayload() instanceof BookingCancelled)) {
            seen.add(p.getPayload());
          }
        };
    multicaster.addApplicationListener(listener);
    try {
      action.run();
    } finally {
      multicaster.removeApplicationListener(listener);
    }
    return seen;
  }

  @Test
  void changingToTheNextSlotWhenAlreadyAtTheCapKeepsTheSameBooking() {
    Facility f = facility("cambiar-limite", true);
    UUID use = whole(f, Map.of("rules", Map.of("capCount", 1, "capPeriod", "DAY")));
    String ana = person();
    Instant nine = at(tomorrow(), OPENS);
    Instant ten = at(tomorrow(), 600);
    Api.Response booked = reserve(ana, f, use, nine);
    UUID id = id(booked.json());
    Api.Response second = reserve(ana, f, use, ten);
    assertThat(second.status()).as("xa está no límite").isEqualTo(422);
    assertThat(code(second)).isEqualTo("booking.cap-reached");

    List<Api.Response> responses = new ArrayList<>();
    List<Object> events = events(() -> responses.add(change(ana, f, id, 0, use, ten)));

    Api.Response changed = responses.getFirst();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
    assertThat(changed.etagVersion()).isEqualTo(1);
    JsonNode b = changed.json();
    assertThat(id(b)).isEqualTo(id);
    assertThat(b.get("code").asString()).isEqualTo(code(booked));
    assertThat(Instant.parse(b.get("start").asString())).isEqualTo(ten);
    assertThat(b.get("version").asLong()).isEqualTo(1);
    assertThat(events).containsExactly(new BookingChanged(f.municipality(), id, nine, ten, 1));
    assertThat(stored(id))
        .containsEntry("version", 1L)
        .containsEntry("events", List.of("CREATED", "CHANGED"));
    assertThat(
            count(
                "select count(*) from booking_occupancy where booking_id = :k and active"
                    + " and lower(period) = (select lower(period) from booking where id = :k)",
                id))
        .as("unha soa ocupación activa, a nova")
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    """
                    select lower(from_period) = :nine and lower(to_period) = :ten
                    from booking_event where booking_id = :k and kind = 'CHANGED'
                    """)
                .param("k", id)
                .param("nine", nine.atOffset(java.time.ZoneOffset.UTC))
                .param("ten", ten.atOffset(java.time.ZoneOffset.UTC))
                .query(Boolean.class)
                .single())
        .as("a historia garda o período de antes e o de despois")
        .isTrue();
    // A hora vella quedou libre para outra persoa.
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);
  }

  @Test
  void aTakenSlotAnswers409AndLeavesTheOldBookingIntact() {
    Facility f = facility("cambiar-ocupado", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());
    assertThat(reserve(person(), f, use, at(tomorrow(), 600)).status()).isEqualTo(201);
    Map<String, Object> before = stored(id);

    Api.Response taken = change(ana, f, id, 0, use, at(tomorrow(), 600));

    assertThat(taken.status()).as(taken.toString()).isEqualTo(409);
    assertThat(code(taken)).isEqualTo("booking.slot-taken");
    assertThat(startMinutes(taken.json().get("alternatives")))
        .as("a hora vella volve estar entre as libres mentres se avalía")
        .contains(OPENS);
    assertThat(stored(id)).isEqualTo(before);
    Api.Response detail = api().get(mine(f.slug()) + "/" + id).token(ana).send();
    assertThat(detail.etagVersion()).isZero();
  }

  @Test
  void aFullSeatSlotLeavesTheOldSeatAndCounterAsTheyWere() {
    Facility f = facility("cambiar-prazas", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 2)));
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());
    reserve(person(), f, use, at(tomorrow(), 600));
    reserve(person(), f, use, at(tomorrow(), 600));
    Map<String, Object> before = stored(id);
    assertThat(seatsTaken(use)).containsExactly(1, 2);

    Api.Response full = change(ana, f, id, 0, use, at(tomorrow(), 600));

    assertThat(full.status()).as(full.toString()).isEqualTo(409);
    assertThat(stored(id)).isEqualTo(before);
    assertThat(seatsTaken(use)).as("use_slot sen tocar").containsExactly(1, 2);

    Api.Response moved = change(ana, f, id, 0, use, at(tomorrow(), 660));
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(seatsTaken(use)).as("a praza vella volve e a nova cóntase").containsExactly(0, 2, 1);
  }

  @Test
  void anotherFacilityIsRefusedAndAnotherPersonSeesNothing() {
    Facility f = facility("cambiar-outra", true);
    Facility other = facility(f.slug(), f.municipality(), "Campo da Gándara", true);
    UUID use = whole(f, Map.of());
    UUID elsewhere = whole(other, Map.of());
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());
    Map<String, Object> before = stored(id);

    Api.Response otherFacility = change(ana, f, id, 0, elsewhere, at(tomorrow(), OPENS));
    Api.Response notMine = change(person(), f, id, 0, use, at(tomorrow(), 600));
    Api.Response notMineCancel = cancel(person(), f, id, 0);

    assertThat(otherFacility.status()).as(otherFacility.toString()).isEqualTo(422);
    assertThat(code(otherFacility)).isEqualTo("booking.change-other-facility");
    assertThat(notMine.status()).isEqualTo(404);
    assertThat(code(notMine)).isEqualTo("booking.not-found");
    assertThat(notMineCancel.status()).isEqualTo(404);
    assertThat(stored(id)).isEqualTo(before);
  }

  @Test
  void outOfTheDeadlineNeitherChangesNorCancelsOnline() {
    Facility f = facility("cambiar-prazo", true);
    UUID use = whole(f, Map.of("rules", Map.of("cancelHours", 72)));
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());

    Api.Response changed = change(ana, f, id, 0, use, at(tomorrow(), 600));
    Api.Response cancelled = cancel(ana, f, id, 0);

    assertThat(changed.status()).as(changed.toString()).isEqualTo(422);
    assertThat(code(changed)).isEqualTo("booking.cancel-deadline-passed");
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(422);
    assertThat(code(cancelled)).isEqualTo("booking.cancel-deadline-passed");
    assertThat(stored(id)).containsEntry("version", 0L).containsEntry("status", "CONFIRMED");
  }

  @Test
  void aPaidBookingOnlyChangesToTheSamePrice() {
    Facility f = facility("cambiar-pagada", true);
    UUID use = whole(f, Map.of("price", Map.of("kind", "PAID", "amountCents", 800)));
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());
    markPaid(id);

    Api.Response longer = change(ana, f, id, 0, use, at(tomorrow(), 600), 2);
    Api.Response same = change(ana, f, id, 0, use, at(tomorrow(), 600), 1);

    assertThat(longer.status()).as(longer.toString()).isEqualTo(422);
    assertThat(code(longer)).isEqualTo("booking.change-price-paid");
    assertThat(same.status()).as(same.toString()).isEqualTo(200);
    assertThat(same.json().get("paymentStatus").asString()).isEqualTo("PAID");
  }

  @Test
  void cancellingAPaidBookingLeavesItToRefundFreesTheSlotAndTwiceIs409() {
    Facility f = facility("cancelar-pagada", true);
    UUID use = whole(f, Map.of("price", Map.of("kind", "PAID", "amountCents", 800)));
    String ana = person();
    Instant nine = at(tomorrow(), OPENS);
    UUID id = id(reserve(ana, f, use, nine).json());
    markPaid(id);

    List<Api.Response> responses = new ArrayList<>();
    List<Object> events = events(() -> responses.add(cancel(ana, f, id, 0)));

    Api.Response cancelled = responses.getFirst();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(cancelled.etagVersion()).isEqualTo(1);
    JsonNode b = cancelled.json();
    assertThat(b.get("status").asString()).isEqualTo("CANCELLED_BY_PERSON");
    assertThat(b.get("paymentStatus").asString()).isEqualTo("TO_REFUND");
    assertThat(b.get("canCancel").asBoolean()).isFalse();
    assertThat(events).containsExactly(BookingCancelled.byPerson(f.municipality(), id));
    assertThat(stored(id)).containsEntry("events", List.of("CREATED", "CANCELLED_BY_PERSON"));
    assertThat(count("select count(*) from booking_occupancy where booking_id = :k and active", id))
        .isZero();
    assertThat(reserve(person(), f, use, nine).status()).as("o oco queda libre").isEqualTo(201);

    Api.Response twice = cancel(ana, f, id, 1);
    assertThat(twice.status()).as(twice.toString()).isEqualTo(409);
    assertThat(code(twice)).isEqualTo("booking.not-cancellable");
    Api.Response changeCancelled = change(ana, f, id, 1, use, at(tomorrow(), 600));
    assertThat(changeCancelled.status()).isEqualTo(409);
    assertThat(code(changeCancelled)).isEqualTo("booking.not-cancellable");
  }

  @Test
  void cancellingAFreeSeatGivesItBackWithoutADeadlineRule() {
    Facility f = facility("cancelar-praza", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 2)));
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS), 2, null).json());
    assertThat(seatsTaken(use)).containsExactly(1, 1);

    Api.Response cancelled = cancel(ana, f, id, 0);

    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(cancelled.json().get("paymentStatus").asString()).isEqualTo("NONE");
    assertThat(seatsTaken(use)).containsExactly(0, 0);
  }

  @Test
  void bothNeedAnIfMatchWithTheCurrentVersion() {
    Facility f = facility("cambiar-version", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", use);
    body.put("start", at(tomorrow(), 600).toString());
    body.put("slots", 1);

    Api.Response changeWithout =
        api().put(mine(f.slug()) + "/" + id + "/slot", body).token(ana).send();
    Api.Response cancelWithout =
        api().post(mine(f.slug()) + "/" + id + "/cancel", null).token(ana).send();
    Api.Response changeStale = change(ana, f, id, 7, use, at(tomorrow(), 600));
    Api.Response cancelStale = cancel(ana, f, id, 7);

    assertThat(changeWithout.status()).as(changeWithout.toString()).isEqualTo(428);
    assertThat(cancelWithout.status()).as(cancelWithout.toString()).isEqualTo(428);
    assertThat(changeStale.status()).as(changeStale.toString()).isEqualTo(412);
    assertThat(code(changeStale)).isEqualTo("common.stale-version");
    assertThat(cancelStale.status()).isEqualTo(412);
    assertThat(stored(id)).containsEntry("version", 0L).containsEntry("status", "CONFIRMED");
  }

  /** O cobro no mostrador aínda non ten API (D2-07): márcase na base. */
  void markPaid(UUID booking) {
    jdbc.sql("update booking set payment_status = 'PAID' where id = :k")
        .param("k", booking)
        .update();
  }
}
