// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cambiar as prazas dun uso que xa ten contadores en {@code use_slot} (ADR-0012, ADR-0022): a copia
 * de {@code capacity} segue á do uso nas franxas que aínda non empezaron, ao subir e ao baixar, con
 * e sen reservas. Baixar con reservas futuras segue sendo 409 {@code facility.use-has-bookings}
 * (P1).
 */
class SeatCapacityIT extends AbstractBookingIT {

  @Test
  void raisingTheSeatsOfASlotAlreadyFullLetsTheNextPersonIn() {
    Facility f = facility("prazas-subir", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 2)));
    Instant nine = at(tomorrow(), OPENS);
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);
    Api.Response full = reserve(person(), f, use, nine);
    assertThat(full.status()).as(full.toString()).isEqualTo(409);
    assertThat(code(full)).isEqualTo("booking.slot-taken");

    Api.Response raised = seats(f, use, 0, 3);

    assertThat(raised.status()).as(raised.toString()).isEqualTo(200);
    assertThat(capacity(use, nine)).isEqualTo(3);
    Api.Response third = reserve(person(), f, use, nine);
    assertThat(third.status()).as(third.toString()).isEqualTo(201);
    assertThat(count("select taken from use_slot where use_id = :k", use)).isEqualTo(3);
  }

  @Test
  void loweringTheSeatsWithFutureBookingsIsRefusedAndTheCounterStays() {
    Facility f = facility("prazas-baixar-con", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 3)));
    Instant nine = at(tomorrow(), OPENS);
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);

    Api.Response lowered = seats(f, use, 0, 2);

    assertThat(lowered.status()).as(lowered.toString()).isEqualTo(409);
    assertThat(code(lowered)).isEqualTo("facility.use-has-bookings");
    assertThat(capacity(use, nine)).isEqualTo(3);
  }

  @Test
  void loweringTheSeatsAfterTheBookingsWereCancelledAlsoLowersTheCounters() {
    Facility f = facility("prazas-baixar-sen", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 3)));
    Instant nine = at(tomorrow(), OPENS);
    String ana = person();
    UUID booking = id(reserve(ana, f, use, nine).json());
    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + booking + "/cancel", null).ifMatch(0).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);

    Api.Response lowered = seats(f, use, 0, 2);

    assertThat(lowered.status()).as(lowered.toString()).isEqualTo(200);
    assertThat(capacity(use, nine)).isEqualTo(2);
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);
    Api.Response third = reserve(person(), f, use, nine);
    assertThat(third.status()).as(third.toString()).isEqualTo(409);
    assertThat(code(third)).isEqualTo("booking.slot-taken");
  }

  @Test
  void changingTheSeatsOfAUseWithoutCountersOnlyChangesTheUse() {
    Facility f = facility("prazas-sen-contadores", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 3)));

    Api.Response raised = seats(f, use, 0, 4);
    Api.Response lowered = seats(f, use, 1, 2);

    assertThat(raised.status()).as(raised.toString()).isEqualTo(200);
    assertThat(lowered.status()).as(lowered.toString()).isEqualTo(200);
    assertThat(count("select count(*) from use_slot where use_id = :k", use)).isZero();
    Instant nine = at(tomorrow(), OPENS);
    assertThat(reserve(person(), f, use, nine).status()).isEqualTo(201);
    assertThat(capacity(use, nine)).isEqualTo(2);
  }

  /** Cambia só as prazas do uso (o resto, como o crea {@link #use}). */
  private Api.Response seats(Facility f, UUID use, long version, int capacity) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Spinning");
    body.put("rentalMode", "SEATS");
    body.put("slotMinutes", 60);
    body.put("availability", "ALWAYS");
    body.put("capacity", capacity);
    return api()
        .put(facilities(f.slug()) + "/" + f.id() + "/uses/" + use, body)
        .ifMatch(version)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  private int capacity(UUID use, Instant slotStart) {
    return jdbc.sql("select capacity from use_slot where use_id = :k and slot_start = :s")
        .param("k", use)
        .param("s", java.sql.Timestamp.from(slotStart))
        .query(Integer.class)
        .single();
  }
}
