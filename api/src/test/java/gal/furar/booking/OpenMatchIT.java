// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Quen ten a reserva busca xente para xogar (ADR-0040, punto 4): abrir, editar co {@code If-Match},
 * «Deixar de buscar» e volver buscar; e as comprobacións de abrir: función acesa, reserva propia,
 * de pista enteira ou por partes, co nome no token (nunca o correo) e só mentres a reserva se pode
 * cancelar de balde e antes do prazo do concello.
 */
class OpenMatchIT extends AbstractOpenMatchIT {

  @Test
  void opensAndEditsTheirOwnBookingWithTheirNameNeverTheEmail() {
    Facility f = facility("partida-abrir", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String sub = "ana-" + UUID.randomUUID();
    UUID booking = booked(person(sub), f, use, at(later(), 600));

    Api.Response created = open(person(sub), f, booking, terms(2, 4, 10, false));

    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.header("Location")).endsWith("/me/open-matches/" + id(created.json()));
    assertThat(created.header("ETag")).isEqualTo("\"0\"");
    assertThat(created.header("X-Robots-Tag")).isEqualTo("noindex");
    JsonNode body = created.json();
    assertThat(body.get("organizerName").asString()).isEqualTo("Persoa " + sub);
    assertThat(body.get("me").get("role").asString()).isEqualTo("ORGANIZER");
    assertThat(body.get("bookingId").asString()).isEqualTo(booking.toString());
    assertThat(body.get("total").asInt()).isEqualTo(2);
    assertThat(body.get("status").asString()).isEqualTo("OPEN");
    assertThat(body.get("deadline").asString())
        .isEqualTo(at(later(), 600).minusSeconds(12 * 3600).toString());
    assertThat(created.body()).doesNotContain(email(sub));

    // Editar pide If-Match.
    Api.Response noVersion = open(person(sub), f, booking, terms(3, 4, 8, true));
    assertThat(noVersion.status()).isEqualTo(428);
    Api.Response edited =
        api()
            .put(me(f.slug()) + "/bookings/" + booking + "/open-match", terms(3, 4, 8, true))
            .ifMatch(0)
            .token(person(sub))
            .send();
    assertThat(edited.status()).as(edited.toString()).isEqualTo(200);
    assertThat(edited.header("ETag")).isEqualTo("\"1\"");
    assertThat(edited.json().get("maximum").asInt()).isEqualTo(8);
    assertThat(edited.json().get("approval").asBoolean()).isTrue();

    // A reserva aparece «buscando xente» en «As miñas reservas».
    JsonNode mine = api().get(mine(f.slug()) + "/" + booking).token(person(sub)).send().json();
    assertThat(mine.get("openMatch").get("status").asString()).isEqualTo("OPEN");
    assertThat(mine.get("openMatch").get("total").asInt()).isEqualTo(3);

    // Outra persoa non abre a reserva allea.
    assertThat(problem(open(person(), f, booking, terms(2, 4, 10, false))))
        .isEqualTo("404 booking.not-found");
  }

  @Test
  void withTheFunctionOffItDoesNotExist() {
    Facility f = facility("partida-apagada", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));

    assertThat(problem(open(ana, f, booking, terms(2, 4, 10, false))))
        .isEqualTo("404 booking.matches-off");
  }

  @Test
  void aTokenWithoutNameNeverPublishesTheEmail() {
    Facility f = facility("partida-sen-nome", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String sub = "sennome-" + UUID.randomUUID();
    String token = TestTokens.withoutRolesClaim(sub, Map.of("locale", "gl"));
    UUID booking = booked(token, f, use, at(later(), 600));

    assertThat(problem(open(token, f, booking, terms(2, 4, 10, false))))
        .isEqualTo("422 booking.match-name-required");
    assertThat(count("select count(*) from booking_open_match where booking_id = :k", booking))
        .isZero();
  }

  @Test
  void onlyWholeOrPartsAndOnlyWhileItCancelsForFree() {
    Facility f = facility("partida-regras", true);
    openMatches(f, true, 12);
    UUID seats = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 10)));
    UUID late = whole(f, "Pista con prazo", Map.of("rules", Map.of("cancelHours", 168)));
    String ana = person();
    UUID bySeats = booked(ana, f, seats, at(later(), 600));
    UUID tooLate = booked(ana, f, late, at(later(), 660));

    assertThat(problem(open(ana, f, bySeats, terms(2, 4, 10, false))))
        .isEqualTo("422 booking.match-seats-use");
    // A reserva xa non se cancela de balde (7 días antes): abrila daría unha cancelación de balde.
    Api.Response refused = open(ana, f, tooLate, terms(2, 4, 10, false));
    assertThat(problem(refused)).isEqualTo("422 booking.match-too-late");
    assertThat(refused.json().get("params").get("deadline").asString()).isNotBlank();
  }

  @Test
  void theMunicipalityDeadlineAlsoCloses() {
    Facility f = facility("partida-prazo", true);
    openMatches(f, true, 72);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(tomorrow(), 600));

    assertThat(problem(open(ana, f, booking, terms(2, 4, 10, false))))
        .isEqualTo("422 booking.match-too-late");
  }

  @Test
  void impossibleCountsAndAMaximumBelowWhoAlreadyGoes() {
    Facility f = facility("partida-numeros", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));

    assertThat(problem(open(ana, f, booking, terms(4, 2, 4, false))))
        .isEqualTo("422 booking.match-counts-invalid");
    UUID match = opened(ana, f, booking, 2, 4, 10, false);
    assertThat(enter(person(), f, match).status()).isEqualTo(201);
    assertThat(enter(person(), f, match).status()).isEqualTo(201);

    Api.Response below =
        api()
            .put(me(f.slug()) + "/bookings/" + booking + "/open-match", terms(2, 2, 3, false))
            .ifMatch(0)
            .token(ana)
            .send();
    assertThat(problem(below)).isEqualTo("422 booking.match-maximum-below-count");
    assertThat(below.json().get("params").get("count").asInt()).isEqualTo(4);
  }

  @Test
  void stopLookingHidesItAndDropsTheRequestsButKeepsTheBookingAndThePeople() {
    Facility f = facility("partida-deixar", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));
    UUID match = opened(ana, f, booking, 2, 4, 10, true);
    String xan = person();
    String bea = person();
    assertThat(enter(xan, f, match).status()).isEqualTo(201);
    assertThat(decide(ana, f, match, playerId(xan, f, match), true).status()).isEqualTo(200);
    assertThat(enter(bea, f, match).status()).isEqualTo(201);

    Api.Response closed = status(ana, f, booking, "CLOSED");

    assertThat(closed.status()).as(closed.toString()).isEqualTo(200);
    assertThat(closed.json().get("status").asString()).isEqualTo("CLOSED");
    assertThat(players(match, "PENDING")).as("as solicitudes bórranse").isZero();
    assertThat(players(match, "CONFIRMED")).isEqualTo(1);
    assertThat(publicPage(f, match).status()).isEqualTo(404);
    assertThat(publicList(f)).isEmpty();
    assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
    // Dentro aínda se ve.
    assertThat(mine(xan, f, match).status()).isEqualTo(200);

    Api.Response reopened = status(ana, f, booking, "OPEN");
    assertThat(reopened.json().get("status").asString()).isEqualTo("OPEN");
    assertThat(publicPage(f, match).status()).isEqualTo(200);
  }

  /** A fila da persoa na partida, da súa propia vista. */
  UUID playerId(String token, Facility f, UUID match) {
    return UUID.fromString(mine(token, f, match).json().get("me").get("playerId").asString());
  }
}
