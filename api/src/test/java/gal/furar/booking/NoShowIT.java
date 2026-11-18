// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.NoShowService;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * As faltas de punta a punta (ADR-0032, punto 5): a manual (antes da fin, sobre unha validada ou
 * unha cancelada, desfacer) e a tarefa das instalacións con control de entrada (só desde que se
 * acendeu e pasada a marxe, non volve marcar o desfeito, e unha validación simultánea da mesma
 * reserva deixa unha marca coherente).
 */
class NoShowIT extends AbstractAttendanceIT {

  @Autowired NoShowService task;

  UUID past(Facility f, UUID use, int slot, Duration endedAgo) {
    Api.Response booked = reserve(person(), f, use, at(tomorrow(), OPENS + 60 * slot));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID id = id(booked.json());
    move(id, Instant.now().minus(endedAgo).minus(Duration.ofHours(1)));
    return id;
  }

  @Test
  void theStaffMarksTheNoShowOfAnEndedBookingAndUndoesIt() {
    Facility f = facility("falta-man", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    UUID upcoming = id(reserve(person(), f, use, at(tomorrow(), OPENS)).json());
    UUID ended = past(f, use, 1, Duration.ofMinutes(5));

    assertThat(detail(staff, f.slug(), upcoming).get("canMarkNoShow").asBoolean()).isFalse();
    Api.Response early = noShow(staff, f.slug(), upcoming);
    assertThat(early.status()).isEqualTo(422);
    assertThat(code(early)).isEqualTo("booking.no-show-too-early");

    assertThat(detail(staff, f.slug(), ended).get("canMarkNoShow").asBoolean()).isTrue();
    Api.Response marked = noShow(staff, f.slug(), ended);
    assertThat(marked.status()).as(marked.toString()).isEqualTo(200);
    JsonNode d = marked.json();
    assertThat(d.get("attendance").asString()).isEqualTo("NO_SHOW");
    assertThat(d.get("status").asString()).isEqualTo("CONFIRMED");
    assertThat(d.get("done").asBoolean()).isTrue();
    assertThat(d.get("canMarkNoShow").asBoolean()).isFalse();
    assertThat(d.get("noShow").isNull()).as("coa penalización apagada, sen aviso").isTrue();
    assertThat(mark(ended)).startsWith("NO_SHOW test-");
    assertThat(history(ended)).containsExactly("CREATED", "NO_SHOW");
    assertThat(
            api()
                .put(bookings(f.slug()) + "/" + ended + "/attendance", Map.of("attendance", "USED"))
                .ifMatch(1)
                .token(staff)
                .send()
                .status())
        .as("á man só se marca a falta")
        .isEqualTo(400);

    Api.Response undone = undo(staff, f.slug(), ended);
    assertThat(undone.status()).as(undone.toString()).isEqualTo(200);
    assertThat(undone.json().get("attendance").asString()).isEqualTo("NONE");
    assertThat(undone.json().get("canMarkNoShow").asBoolean()).as("o botón volve saír").isTrue();
    assertThat(audit(f.municipality(), "booking.no-show"))
        .containsExactly("FAILURE booking.no-show-too-early", "SUCCESS ");
  }

  @Test
  void aValidatedOrCancelledBookingIsNotMarked() {
    Facility f = facility("falta-non", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    String manager = TestTokens.manager(f.slug());
    Api.Response booked = reserve(person(), f, use, at(tomorrow(), OPENS));
    UUID used = id(booked.json());
    move(used, Instant.now().minus(Duration.ofMinutes(70)));
    assertThat(
            validate(staff, f.slug(), booked.json().get("code").asString())
                .json()
                .get("outcome")
                .asString())
        .isEqualTo("VALIDATED");
    UUID cancelled = id(reserve(person(), f, use, at(tomorrow(), OPENS + 60)).json());
    assertThat(
            api()
                .post(
                    bookings(f.slug()) + "/" + cancelled + "/cancel", Map.of("channels", List.of()))
                .ifMatch(0)
                .token(manager)
                .send()
                .status())
        .isEqualTo(200);
    move(cancelled, Instant.now().minus(Duration.ofHours(3)));

    Api.Response overUsed = noShow(staff, f.slug(), used);
    assertThat(overUsed.status()).isEqualTo(409);
    assertThat(code(overUsed)).isEqualTo("booking.already-validated");
    Api.Response overCancelled = noShow(staff, f.slug(), cancelled);
    assertThat(overCancelled.status()).isEqualTo(409);
    assertThat(code(overCancelled)).isEqualTo("booking.attendance-cancelled");
    assertThat(
            api()
                .put(
                    bookings(f.slug()) + "/" + used + "/attendance",
                    Map.of("attendance", "NO_SHOW"))
                .ifMatch(1)
                .token(TestTokens.platformAdmin())
                .send()
                .status())
        .isEqualTo(403);
  }

  @Test
  void theTaskMarksOnlyWithEntryControlSinceItWasTurnedOnAndOnceTheMarginHasPassed() {
    Facility controlled = facility("falta-tarefa", true);
    Facility open = facility(controlled.slug(), controlled.municipality(), "Pista sen porta", true);
    UUID use = whole(controlled, Map.of());
    UUID openUse = whole(open, Map.of());
    String staff = TestTokens.staff(controlled.slug());
    Instant since = Instant.now().minus(Duration.ofHours(6));
    entryControl(controlled, since);

    UUID missed = past(controlled, use, 0, Duration.ofHours(2));
    UUID stillInMargin = past(controlled, use, 1, Duration.ofMinutes(30));
    UUID beforeTheSwitch = past(controlled, use, 2, Duration.ofHours(8));
    UUID undone = past(controlled, use, 3, Duration.ofHours(3));
    assertThat(noShow(staff, controlled.slug(), undone).status()).isEqualTo(200);
    assertThat(undo(staff, controlled.slug(), undone).status()).isEqualTo(200);
    Api.Response usedBooking = reserve(person(), controlled, use, at(tomorrow(), OPENS + 240));
    UUID used = id(usedBooking.json());
    move(used, Instant.now().minus(Duration.ofHours(4)));
    assertThat(
            validate(
                    staff,
                    controlled.slug(),
                    usedBooking.json().get("code").asString(),
                    true,
                    "Veu")
                .status())
        .isEqualTo(200);
    Api.Response elsewhere = reserve(person(), open, openUse, at(tomorrow(), OPENS));
    UUID withoutControl = id(elsewhere.json());
    move(withoutControl, Instant.now().minus(Duration.ofHours(3)));

    task.apply();

    assertThat(mark(missed)).isEqualTo("NO_SHOW furar:no-show");
    assertThat(history(missed)).containsExactly("CREATED", "NO_SHOW");
    assertThat(mark(stillInMargin)).as("a marxe aínda non pasou").isEqualTo("NONE -");
    assertThat(mark(beforeTheSwitch)).as("anterior a acender o control").isEqualTo("NONE -");
    assertThat(mark(undone)).as("o desfeito non se volve marcar").startsWith("NONE test-");
    assertThat(mark(used)).startsWith("USED test-");
    assertThat(mark(withoutControl)).as("sen control de entrada").isEqualTo("NONE -");
    assertThat(
            jdbc.sql(
                    """
                    select actor_subject || ' ' || (details ->> 'count') || ' '
                      || (details ->> 'blocks')
                    from audit_event
                    where municipality_id = :m and action = 'booking.no-show'
                      and actor_subject = 'furar:no-show'
                    """)
                .param("m", controlled.municipality())
                .query(String.class)
                .list())
        .as("un evento por lote, coa tarefa como quen actúa e só cantas")
        .containsExactly("furar:no-show 1 0");

    // Idempotente: outra pasada non atopa nada novo neste concello.
    task.apply();
    assertThat(history(missed)).containsExactly("CREATED", "NO_SHOW");
    JsonNode seen = detail(staff, controlled.slug(), missed);
    assertThat(seen.get("attendance").asString()).isEqualTo("NO_SHOW");
  }

  @Test
  void theTaskAndAValidationOfTheSameBookingLeaveOneCoherentMark() throws Exception {
    Facility f = facility("falta-vez", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    entryControl(f, Instant.now().minus(Duration.ofHours(6)));
    Api.Response booked = reserve(person(), f, use, at(tomorrow(), OPENS));
    UUID id = id(booked.json());
    String code = booked.json().get("code").asString();
    move(id, Instant.now().minus(Duration.ofHours(3)));

    List<Api.Response> both =
        together(
            List.of(
                () -> validate(staff, f.slug(), code, true, "Veu e non se leu o QR"),
                () -> {
                  task.apply();
                  return validate(staff, f.slug(), "FUR-ZZZZ");
                }));

    assertThat(both.get(0).status()).as(both.get(0).toString()).isEqualTo(200);
    // Se a tarefa chegou antes, a validación quita a falta; se chegou despois, sáltaa.
    assertThat(mark(id)).startsWith("USED test-");
    assertThat(history(id)).filteredOn("VALIDATED"::equals).hasSize(1);
    assertThat(history(id).getLast()).isEqualTo("VALIDATED");
    task.apply();
    assertThat(mark(id)).as("a tarefa non pisa unha validación").startsWith("USED test-");
  }
}
