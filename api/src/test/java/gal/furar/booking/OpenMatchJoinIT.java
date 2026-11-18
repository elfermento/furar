// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Unirse, pedilo, saír e decidir (ADR-0040, puntos 4 e 5): as prazas cóntanse baixo o bloqueo da
 * fila da partida (dúas persoas á vez pola última praza: unha entra), con aprobación quen organiza
 * acepta ou rexeita, e non se unen as contas de traballo, as bloqueadas por faltas nin os tokens
 * sen nome.
 */
class OpenMatchJoinIT extends AbstractOpenMatchIT {

  record Ready(Facility f, String organizer, UUID booking, UUID match) {}

  Ready ready(String prefix, int known, int minimum, int maximum, boolean approval) {
    Facility f = facility(prefix, true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));
    return new Ready(f, ana, booking, opened(ana, f, booking, known, minimum, maximum, approval));
  }

  UUID playerId(String token, Facility f, UUID match) {
    return UUID.fromString(mine(token, f, match).json().get("me").get("playerId").asString());
  }

  @Test
  void withoutApprovalWhoJoinsIsInsideAndJoiningTwiceChangesNothing() {
    Ready r = ready("partida-unirse", 2, 4, 10, false);
    String xan = person();

    Api.Response joined = enter(xan, r.f(), r.match());

    assertThat(joined.status()).as(joined.toString()).isEqualTo(201);
    assertThat(joined.json().get("me").get("role").asString()).isEqualTo("CONFIRMED");
    assertThat(joined.json().get("total").asInt()).isEqualTo(3);
    assertThat(enter(xan, r.f(), r.match()).status()).isEqualTo(200);
    assertThat(players(r.match(), "CONFIRMED")).isEqualTo(1);
    assertThat(problem(enter(r.organizer(), r.f(), r.match()))).isEqualTo("409 booking.match-own");
    assertThat(
            count(
                "select count(*) from notification_delivery where booking_id = :k"
                    + " and kind like 'MATCH_%'",
                r.booking()))
        .as("unirse directamente non manda correo")
        .isZero();
  }

  @Test
  void twoPeopleAtOnceForTheLastSeatOnlyOneEnters() throws Exception {
    Ready r = ready("partida-ultima", 2, 3, 3, false);
    List<Callable<Api.Response>> calls =
        List.of(() -> enter(person(), r.f(), r.match()), () -> enter(person(), r.f(), r.match()));

    List<Api.Response> responses = together(calls);

    assertThat(byStatus(responses)).containsEntry(201, 1L).containsEntry(409, 1L);
    assertThat(
            responses.stream().filter(x -> x.status() == 409).map(AbstractWaitIT::problem).toList())
        .containsExactly("409 booking.match-full");
    assertThat(players(r.match(), "CONFIRMED")).isEqualTo(1);
  }

  @Test
  void withApprovalTheOrganizerAcceptsOrRejectsAndARejectedPersonCannotAskAgain() {
    Ready r = ready("partida-aprobar", 2, 3, 3, true);
    String xan = person();
    String bea = person();
    String rui = person();

    Api.Response asked = enter(xan, r.f(), r.match());
    assertThat(asked.status()).isEqualTo(201);
    assertThat(asked.json().get("me").get("role").asString()).isEqualTo("PENDING");
    assertThat(asked.json().get("total").asInt()).as("as pendentes non ocupan sitio").isEqualTo(2);
    assertThat(enter(bea, r.f(), r.match()).status()).isEqualTo(201);
    assertThat(enter(rui, r.f(), r.match()).status()).isEqualTo(201);

    JsonNode organizer = mine(r.organizer(), r.f(), r.match()).json();
    assertThat(organizer.get("requests")).hasSize(3);
    assertThat(organizer.get("requests").get(0).get("name").asString()).startsWith("Persoa ");

    UUID xanRow = playerId(xan, r.f(), r.match());
    UUID beaRow = playerId(bea, r.f(), r.match());
    UUID ruiRow = playerId(rui, r.f(), r.match());
    Api.Response accepted = decide(r.organizer(), r.f(), r.match(), xanRow, true);
    assertThat(accepted.status()).as(accepted.toString()).isEqualTo(200);
    assertThat(accepted.json().get("players")).hasSize(1);
    assertThat(problem(decide(r.organizer(), r.f(), r.match(), xanRow, false)))
        .isEqualTo("409 booking.match-player-decided");
    // 2 + 1 de 3: completa; aceptar outra xa non cabe.
    assertThat(problem(decide(r.organizer(), r.f(), r.match(), beaRow, true)))
        .isEqualTo("409 booking.match-full");
    assertThat(decide(r.organizer(), r.f(), r.match(), ruiRow, false).status()).isEqualTo(200);
    assertThat(problem(enter(rui, r.f(), r.match()))).isEqualTo("409 booking.match-rejected");
    // Quen non organiza non decide.
    assertThat(problem(decide(xan, r.f(), r.match(), beaRow, true)))
        .isEqualTo("404 booking.match-not-found");
  }

  @Test
  void twoAcceptancesAtOnceForTheLastSeat() throws Exception {
    Ready r = ready("partida-aceptar", 2, 3, 3, true);
    String xan = person();
    String bea = person();
    enter(xan, r.f(), r.match());
    enter(bea, r.f(), r.match());
    UUID xanRow = playerId(xan, r.f(), r.match());
    UUID beaRow = playerId(bea, r.f(), r.match());

    List<Api.Response> responses =
        together(
            List.of(
                () -> decide(r.organizer(), r.f(), r.match(), xanRow, true),
                () -> decide(r.organizer(), r.f(), r.match(), beaRow, true)));

    assertThat(byStatus(responses)).containsEntry(200, 1L).containsEntry(409, 1L);
    assertThat(players(r.match(), "CONFIRMED")).isEqualTo(1);
  }

  @Test
  void workAccountsBlockedPeopleAndTokensWithoutNameDoNotJoin() {
    Ready r = ready("partida-quen", 2, 4, 10, false);
    String worker = "traballo-" + UUID.randomUUID();
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at, version)
            values (:id, :m, :email, 'Persoa de proba', :subject, 'STAFF', 'ACTIVE', 'gl',
                    'sub-semente', now(), now(), now(), now(), 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", r.f().municipality())
        .param("email", email(worker))
        .param("subject", worker)
        .update();
    assertThat(problem(enter(person(worker), r.f(), r.match())))
        .isEqualTo("403 booking.staff-account");
    String blocked = "faltas-" + UUID.randomUUID();
    blockByNoShows(r.f(), email(blocked));
    assertThat(problem(enter(person(blocked), r.f(), r.match())))
        .isEqualTo("403 booking.blocked-no-show");
    String nameless = TestTokens.withoutRolesClaim("sennome-" + UUID.randomUUID(), Map.of());
    assertThat(problem(enter(nameless, r.f(), r.match())))
        .isEqualTo("422 booking.match-name-required");
    assertThat(players(r.match(), "CONFIRMED")).isZero();
  }

  @Test
  void aHiddenOrClosedMatchDoesNotAdmitAnyone() {
    Ready r = ready("partida-pechada", 2, 4, 10, false);
    assertThat(hide(r.f(), r.booking(), true).status()).isEqualTo(200);
    assertThat(problem(enter(person(), r.f(), r.match()))).isEqualTo("404 booking.match-not-found");
    assertThat(hide(r.f(), r.booking(), false).status()).isEqualTo(200);
    assertThat(status(r.organizer(), r.f(), r.booking(), "CLOSED").status()).isEqualTo(200);
    assertThat(problem(enter(person(), r.f(), r.match()))).isEqualTo("409 booking.match-closed");
  }

  @Test
  void leavingFreesTheSeatUntilItStarts() {
    Ready r = ready("partida-sair", 2, 3, 3, false);
    String xan = person();
    assertThat(enter(xan, r.f(), r.match()).status()).isEqualTo(201);
    assertThat(problem(enter(person(), r.f(), r.match()))).isEqualTo("409 booking.match-full");

    assertThat(exit(xan, r.f(), r.match()).status()).isEqualTo(204);
    assertThat(players(r.match(), "CONFIRMED")).isZero();
    assertThat(enter(xan, r.f(), r.match()).status()).isEqualTo(201);

    // Xa empezou: non se sae.
    OffsetDateTime past = Instant.now().minusSeconds(600).atOffset(ZoneOffset.UTC);
    jdbc.sql(
            "update booking set period = tstzrange(:from, :to, '[)'), cancel_until = :from"
                + " where id = :id")
        .param("from", past)
        .param("to", past.plusHours(1))
        .param("id", r.booking())
        .update();
    assertThat(problem(exit(xan, r.f(), r.match()))).isEqualTo("409 booking.match-started");
  }
}
