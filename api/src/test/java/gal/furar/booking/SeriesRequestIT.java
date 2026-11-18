// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As solicitudes de tempada (ADR-0039, puntos 10 e 11): a entidade pide, retira e ve a resposta; o
 * xestor aproba axustando os datos (unha serie aínda que se aprobe dúas veces á vez) ou rexeita
 * cunha mensaxe. Unha instalación con solicitudes non se borra.
 */
class SeriesRequestIT extends AbstractSeasonIT {

  record Ready(Facility f, UUID use, UUID club, String token, Map<String, Object> schedule) {}

  Ready ready(String prefix) {
    Facility f = facility(prefix, true);
    UUID use = whole(f, Map.of());
    String bea = "bea-" + UUID.randomUUID();
    UUID club = organization(f, "Club Demo Patinaxe", email(bea));
    LocalDate thursday = nextWorking(4);
    return new Ready(
        f,
        use,
        club,
        person(bea),
        schedule(club, f, use, null, List.of(4), 600, 690, thursday, thursday.plusDays(14)));
  }

  private Api.Response approve(Ready r, UUID request, Map<String, Object> schedule) {
    Map<String, Object> body = new LinkedHashMap<>(schedule);
    body.put("skip", List.of());
    return api()
        .post(requests(r.f().slug()) + "/" + request + "/approve", body)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .token(TestTokens.manager(r.f().slug()))
        .send();
  }

  private Api.Response reject(Ready r, UUID request, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (message != null) {
      body.put("message", message);
    }
    return api()
        .post(requests(r.f().slug()) + "/" + request + "/reject", body)
        .token(TestTokens.manager(r.f().slug()))
        .send();
  }

  private int pending(Ready r) {
    return api()
        .get(requests(r.f().slug()) + "/pending-count")
        .token(TestTokens.manager(r.f().slug()))
        .send()
        .json()
        .get("count")
        .asInt();
  }

  @Test
  void theOrganizationRequestsAndTheManagerApprovesAdjusting() {
    Ready r = ready("solicitude");

    Api.Response asked = request(r.token(), r.f(), r.club(), r.schedule());
    assertThat(asked.status()).as(asked.toString()).isEqualTo(201);
    assertThat(asked.header("Location")).contains("/series-requests/");
    UUID request = id(asked.json());
    assertThat(asked.json().get("status").asString()).isEqualTo("PENDING");
    assertThat(pending(r)).isEqualTo(1);
    JsonNode card =
        api()
            .get(requests(r.f().slug()))
            .token(TestTokens.manager(r.f().slug()))
            .send()
            .json()
            .get(0);
    assertThat(card.get("organizationName").asString()).isEqualTo("Club Demo Patinaxe");
    assertThat(card.get("note").asString()).isEqualTo("Equipos infantís");
    assertThat(
            api()
                .get(organizations(r.f().slug()))
                .token(TestTokens.manager(r.f().slug()))
                .send()
                .json()
                .get(0)
                .get("status")
                .asString())
        .isEqualTo("REQUEST");

    // Unha instalación con solicitudes non se borra: o erro de sempre, nunca un 500.
    long version =
        api()
            .get(facilities(r.f().slug()) + "/" + r.f().id())
            .token(TestTokens.manager(r.f().slug()))
            .send()
            .etagVersion();
    Api.Response removed =
        api()
            .delete(facilities(r.f().slug()) + "/" + r.f().id())
            .ifMatch(version)
            .token(TestTokens.manager(r.f().slug()))
            .send();
    assertThat(problem(removed)).isEqualTo("409 facility.has-bookings");

    // Aprobar axustando a hora.
    Map<String, Object> adjusted = new LinkedHashMap<>(r.schedule());
    adjusted.put("startMinute", 660);
    adjusted.put("endMinute", 750);
    Api.Response approved = approve(r, request, adjusted);
    assertThat(approved.status()).as(approved.toString()).isEqualTo(201);
    assertThat(approved.json().get("requestId").asString()).isEqualTo(request.toString());
    assertThat(approved.json().get("startMinute").asInt()).isEqualTo(660);
    assertThat(approved.json().get("sessions").asInt()).isEqualTo(3);
    // O uso non ten prezo: nace gratis, coma a que crea o xestor.
    assertThat(approved.json().get("paymentStatus").asString()).isEqualTo("FREE");
    assertThat(pending(r)).isZero();
    assertThat(problem(approve(r, request, adjusted)))
        .isEqualTo("409 booking.series-request-decided");
    assertThat(problem(reject(r, request, "Non"))).isEqualTo("409 booking.series-request-decided");
    JsonNode mine =
        api()
            .get(myOrganizations(r.f().slug()) + "/" + r.club() + "/series-requests")
            .token(r.token())
            .send()
            .json();
    assertThat(mine.get(0).get("status").asString()).isEqualTo("APPROVED");
    // Xa decidida, non se retira.
    assertThat(
            problem(
                api()
                    .delete(
                        myOrganizations(r.f().slug())
                            + "/"
                            + r.club()
                            + "/series-requests/"
                            + request)
                    .token(r.token())
                    .send()))
        .isEqualTo("409 booking.series-request-decided");
  }

  @Test
  void rejectingNeedsAMessageAndTheLimitIsFivePending() {
    Ready r = ready("solicitude-non");
    UUID first = id(request(r.token(), r.f(), r.club(), r.schedule()).json());

    assertThat(problem(reject(r, first, null)))
        .isEqualTo("422 booking.series-request-message-required");
    assertThat(problem(reject(r, first, "   ")))
        .isEqualTo("422 booking.series-request-message-required");
    Api.Response rejected = reject(r, first, "Ese día xa hai <b>outro</b> club.");
    assertThat(rejected.status()).as(rejected.toString()).isEqualTo(200);
    assertThat(rejected.json().get("status").asString()).isEqualTo("REJECTED");
    JsonNode mine =
        api()
            .get(myOrganizations(r.f().slug()) + "/" + r.club() + "/series-requests")
            .token(r.token())
            .send()
            .json();
    assertThat(mine.get(0).get("rejectionMessage").asString())
        .isEqualTo("Ese día xa hai <b>outro</b> club.");

    for (int i = 0; i < 5; i++) {
      assertThat(request(r.token(), r.f(), r.club(), r.schedule()).status()).isEqualTo(201);
    }
    assertThat(problem(request(r.token(), r.f(), r.club(), r.schedule())))
        .isEqualTo("422 booking.series-request-limit");
    // Retirar unha pendente deixa sitio.
    UUID last =
        id(
            api()
                .get(myOrganizations(r.f().slug()) + "/" + r.club() + "/series-requests")
                .token(r.token())
                .send()
                .json()
                .get(0));
    assertThat(
            api()
                .delete(myOrganizations(r.f().slug()) + "/" + r.club() + "/series-requests/" + last)
                .token(r.token())
                .send()
                .status())
        .isEqualTo(204);
    assertThat(request(r.token(), r.f(), r.club(), r.schedule()).status()).isEqualTo(201);
    // Quen non é responsable non pide.
    assertThat(problem(request(person(), r.f(), r.club(), r.schedule())))
        .isEqualTo("404 booking.organization-not-found");
  }

  @Test
  void approvingTwiceAtOnceGivesOneSeriesAndOneConflict() throws Exception {
    Ready r = ready("solicitude-dobre");
    UUID request = id(request(r.token(), r.f(), r.club(), r.schedule()).json());

    List<Callable<Api.Response>> calls =
        List.of(() -> approve(r, request, r.schedule()), () -> approve(r, request, r.schedule()));
    List<Api.Response> responses = together(calls);

    assertThat(byStatus(responses)).containsEntry(201, 1L).containsEntry(409, 1L);
    assertThat(
            count(
                "select count(*) from booking_series where municipality_id = :k",
                r.f().municipality()))
        .isEqualTo(1);
  }

  @Test
  void deletingAndApprovingAtOnceDoNotDeadlock() throws Exception {
    Ready r = ready("solicitude-borrar");
    UUID request = id(request(r.token(), r.f(), r.club(), r.schedule()).json());
    long version =
        api()
            .get(organizations(r.f().slug()) + "/" + r.club())
            .token(TestTokens.manager(r.f().slug()))
            .send()
            .etagVersion();

    List<Callable<Api.Response>> calls =
        List.of(
            () -> approve(r, request, r.schedule()),
            () ->
                api()
                    .delete(organizations(r.f().slug()) + "/" + r.club())
                    .ifMatch(version)
                    .token(TestTokens.manager(r.f().slug()))
                    .send());
    List<Api.Response> responses = together(calls);

    // Sen interbloqueo (nin 500 nin 503): ou se aproba e despois non se borra (ten futuras), ou se
    // borra e a aprobación xa non atopa a entidade.
    assertThat(responses).allSatisfy(x -> assertThat(x.status()).isLessThan(500));
    if (responses.get(0).status() == 201) {
      assertThat(problem(responses.get(1)))
          .isEqualTo("409 booking.organization-has-future-bookings");
    } else {
      assertThat(responses.get(1).status()).isEqualTo(204);
      assertThat(responses.get(0).status()).isEqualTo(404);
    }
  }
}
