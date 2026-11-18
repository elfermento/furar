// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As horas ocupadas na dispoñibilidade dun uso (ADR-0033, punto 2): só que están ocupadas, sen
 * ningún dato de quen as ocupa, e só onde se reserva por internet.
 */
class AvailabilityBusyIT extends AbstractWaitIT {

  @Test
  void busyHoursComeApartFromTheFreeOnesAndSayNothingElse() {
    Facility f = facility("ocupadas", true);
    UUID padel = whole(f, "Pádel", Map.of());
    UUID tennis = whole(f, "Tenis", Map.of());
    LocalDate day = tomorrow();
    String sub = "xan-" + UUID.randomUUID();
    UUID booking = booked(person(sub), f, padel, at(day, 600));
    booked(person(), f, tennis, at(day, 720));

    JsonNode found = availability(f, padel, day);

    assertThat(found.get("bookable").asBoolean()).isTrue();
    assertThat(startMinutes(found.get("slots"))).containsExactly(540, 660, 780);
    assertThat(startMinutes(found.get("busy"))).containsExactly(600, 720);
    JsonNode busy = found.get("busy").get(0);
    assertThat(Instant.parse(busy.get("start").asString())).isEqualTo(at(day, 600));
    assertThat(List.copyOf(busy.propertyNames())).containsExactlyInAnyOrder("start", "startMinute");
    assertThat(found.toString())
        .doesNotContain(sub, booking.toString(), tennis.toString(), "Persoa");
  }

  /** Unha instalación que só amosa os ocos libres non ten onde esperar: sen ocupadas. */
  @Test
  void withoutOnlineBookingThereAreNoBusyHours() {
    Facility f = facility("ocupadas-so-ver", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    booked(person(), f, use, at(day, 600));
    // Deixa de reservarse por internet: só amosa os ocos libres.
    Map<String, Object> details = new HashMap<>();
    details.put("sports", List.of(Map.of("code", "padel")));
    details.put("type", "FIELD");
    details.put("onlineBooking", false);
    details.put("showFreeSlots", true);
    details.put("covered", false);
    Api.Response saved =
        api()
            .put(facilities(f.slug()) + "/" + f.id() + "/details", details)
            .ifMatch(version(f.slug(), f.id()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);

    JsonNode found = availability(f, use, day);

    assertThat(found.get("bookable").asBoolean()).isFalse();
    assertThat(startMinutes(found.get("slots"))).containsExactly(540, 660, 720, 780);
    assertThat(found.get("busy")).isEmpty();
  }
}
