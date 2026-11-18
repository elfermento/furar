// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Modal de reservas afectadas en dous pasos sobre o mesmo endpoint (ADR-0022, punto 6), cun dobre
 * de {@link BookingImpact}: 409 con {@code affected[]}, despois 200 coas decisións; cancelar sen
 * canle → 422.
 */
class FacilityAffectedBookingsIT extends AbstractIT {

  @Autowired ProgrammableBookingImpact impact;

  record Facility(String slug, UUID id, String statusUrl) {}

  Facility facility() {
    String slug = uniqueSlug("afe");
    createMunicipality(slug);
    Api.Response created =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/facilities",
                Map.of("name", "Pabellón de Piñeiros", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    return new Facility(
        slug, id, "/api/v1/municipalities/" + slug + "/facilities/" + id + "/status");
  }

  static AffectedBooking booking(UUID id, boolean email) {
    return new AffectedBooking(
        id, "Uxía Pérez", email, LocalDate.of(2026, 11, 3), 480, 570, "Fútbol 11");
  }

  Api.Response send(Facility f, Map<String, Object> body) {
    return api().put(f.statusUrl(), body).ifMatch(0).token(TestTokens.manager(f.slug())).send();
  }

  @Test
  void closingWithAffectedBookingsAsksAndThenApplies() {
    Facility f = facility();
    UUID b1 = UUID.randomUUID();
    UUID b2 = UUID.randomUUID();
    impact.affect(f.id(), List.of(booking(b1, true), booking(b2, false)));

    Api.Response first = send(f, Map.of("status", "TEMPORARILY_CLOSED"));
    assertThat(first.status()).as(first.toString()).isEqualTo(409);
    assertThat(first.contentType()).startsWith("application/problem+json");
    JsonNode problem = first.json();
    assertThat(problem.get("code").asString()).isEqualTo("facility.affected-bookings");
    assertThat(problem.get("affected").size()).isEqualTo(2);
    JsonNode a = problem.get("affected").get(0);
    assertThat(a.get("bookingId").asString()).isEqualTo(b1.toString());
    assertThat(a.get("date").asString()).isEqualTo("2026-11-03");
    assertThat(a.get("startMinute").asInt()).isEqualTo(480);
    assertThat(a.get("useName").asString()).isEqualTo("Fútbol 11");
    assertThat(a.get("hasEmail").asBoolean()).isTrue();
    assertThat(impact.appliedTo(f.id())).isEmpty();

    Api.Response second =
        send(
            f,
            Map.of(
                "status",
                "TEMPORARILY_CLOSED",
                "decisions",
                List.of(
                    Map.of("bookingId", b1, "action", "CANCEL", "channels", List.of("EMAIL")),
                    Map.of("bookingId", b2, "action", "KEEP", "channels", List.of()))));
    assertThat(second.status()).as(second.toString()).isEqualTo(200);
    assertThat(second.json().get("status").asString()).isEqualTo("TEMPORARILY_CLOSED");
    assertThat(impact.appliedTo(f.id()))
        .singleElement()
        .satisfies(
            applied -> {
              assertThat(applied.change()).isEqualTo(new FacilityChange.Closure(f.id()));
              assertThat(applied.decisions()).hasSize(2);
            });
  }

  @Test
  void cancellingWithoutChannelsIs422() {
    Facility f = facility();
    UUID b1 = UUID.randomUUID();
    impact.affect(f.id(), List.of(booking(b1, true)));
    Api.Response response =
        send(
            f,
            Map.of(
                "status",
                "WITHDRAWN",
                "decisions",
                List.of(Map.of("bookingId", b1, "action", "CANCEL", "channels", List.of()))));
    assertThat(response.status()).as(response.toString()).isEqualTo(422);
    assertThat(response.json().get("code").asString()).isEqualTo("facility.decision-invalid");
    assertThat(impact.appliedTo(f.id())).isEmpty();
  }

  @Test
  void changedBookingsBetweenStepsAre409WithTheNewList() {
    Facility f = facility();
    UUID seen = UUID.randomUUID();
    UUID appeared = UUID.randomUUID();
    impact.affect(f.id(), List.of(booking(seen, true), booking(appeared, true)));
    Api.Response response =
        send(
            f,
            Map.of(
                "status",
                "WITHDRAWN",
                "decisions",
                List.of(Map.of("bookingId", seen, "action", "KEEP"))));
    assertThat(response.status()).as(response.toString()).isEqualTo(409);
    assertThat(response.json().get("affected").size()).isEqualTo(2);
  }
}
