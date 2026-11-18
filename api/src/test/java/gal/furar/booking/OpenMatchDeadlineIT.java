// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.OpenMatchDeadlineService;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O prazo do concello (ADR-0040, punto 7): sen o mínimo, a tarefa cancela sen custo a partida e a
 * reserva e solta a hora (avisa a quen espera); co mínimo, márcaa; unha pechada, oculta ou dun
 * concello coa función apagada non se cancela; dúas pasadas á vez cancelan unha soa vez; e subir as
 * horas nunca cancela en bloque.
 *
 * <p>A reserva é de dentro de dous días ás 10:00 e as horas do concello pásanse a 72 despois de
 * abrir: o prazo xa pasou, pero despois do último cambio da política (30 días atrás).
 */
class OpenMatchDeadlineIT extends AbstractOpenMatchIT {

  @Autowired OpenMatchDeadlineService deadlines;

  record Ready(Facility f, UUID use, String organizer, UUID booking, UUID match) {}

  Ready ready(String prefix, int known, int minimum, int maximum) {
    Facility f = facility(prefix, true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(tomorrow().plusDays(1), 600));
    UUID match = opened(ana, f, booking, known, minimum, maximum, false);
    return new Ready(f, use, ana, booking, match);
  }

  /** O prazo xa pasou: 72 horas antes dunha reserva de dentro de menos de 58 horas. */
  void deadlinePassed(Facility f) {
    jdbc.sql("update municipality set open_match_cancel_hours = 72 where id = :id")
        .param("id", f.municipality())
        .update();
  }

  @Test
  void withoutTheMinimumItCancelsTheBookingForFreeAndReleasesTheHour() {
    Ready r = ready("prazo-sen-minimo", 2, 6, 10);
    assertThat(enter(person(), r.f(), r.match()).status()).isEqualTo(201);
    waiting(person(), r.f(), r.use(), at(tomorrow().plusDays(1), 600));
    deadlinePassed(r.f());

    List<BookingSlotReleased> released = released(deadlines::apply);

    assertThat(matchState(r.match())).isEqualTo("CANCELLED UNFILLED");
    assertThat(bookingStatus(r.booking())).isEqualTo("CANCELLED_BY_MUNICIPALITY");
    assertThat(
            jdbc.sql("select cancelled_by from booking where id = :id")
                .param("id", r.booking())
                .query(String.class)
                .single())
        .isEqualTo("furar:open-match");
    assertThat(history(r.booking())).last().isEqualTo("CANCELLED_BY_MUNICIPALITY");
    assertThat(released).extracting(BookingSlotReleased::useId).contains(r.use());
    // A hora queda libre: outra persoa resérvaa.
    assertThat(reserve(person(), r.f(), r.use(), at(tomorrow().plusDays(1), 600)).status())
        .isEqualTo(201);
    // Repetir non cambia nada.
    deadlines.apply();
    assertThat(history(r.booking())).filteredOn("CANCELLED_BY_MUNICIPALITY"::equals).hasSize(1);
  }

  @Test
  void withTheMinimumItIsMarkedAndNotLookedAtAgain() {
    Ready r = ready("prazo-co-minimo", 2, 3, 10);
    assertThat(enter(person(), r.f(), r.match()).status()).isEqualTo(201);
    deadlinePassed(r.f());

    deadlines.apply();

    assertThat(matchState(r.match())).isEqualTo("OPEN -");
    assertThat(bookingStatus(r.booking())).isEqualTo("CONFIRMED");
    assertThat(
            count(
                "select count(*) from booking_open_match where id = :k"
                    + " and deadline_checked_at is not null",
                r.match()))
        .isEqualTo(1);
    // Saír despois do prazo xa non cancela nada.
    assertThat(players(r.match(), "CONFIRMED")).isEqualTo(1);
  }

  @Test
  void aClosedOrHiddenMatchIsNotCancelled() {
    Ready closed = ready("prazo-pechada", 2, 6, 10);
    assertThat(status(closed.organizer(), closed.f(), closed.booking(), "CLOSED").status())
        .isEqualTo(200);
    deadlinePassed(closed.f());
    Ready hidden = ready("prazo-oculta", 2, 6, 10);
    assertThat(hide(hidden.f(), hidden.booking(), true).status()).isEqualTo(200);
    deadlinePassed(hidden.f());

    deadlines.apply();

    assertThat(matchState(closed.match())).isEqualTo("CLOSED -");
    assertThat(bookingStatus(closed.booking())).isEqualTo("CONFIRMED");
    assertThat(matchState(hidden.match())).isEqualTo("OPEN -");
    assertThat(bookingStatus(hidden.booking())).isEqualTo("CONFIRMED");
  }

  @Test
  void twoPassesAtOnceCancelOnlyOnce() throws Exception {
    Ready r = ready("prazo-dobre", 2, 6, 10);
    deadlinePassed(r.f());

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> a = pool.submit(deadlines::apply);
      Future<Integer> b = pool.submit(deadlines::apply);
      a.get(60, TimeUnit.SECONDS);
      b.get(60, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    assertThat(matchState(r.match())).isEqualTo("CANCELLED UNFILLED");
    assertThat(history(r.booking())).filteredOn("CANCELLED_BY_MUNICIPALITY"::equals).hasSize(1);
  }

  @Test
  void withTheFunctionOffNothingIsCancelled() {
    Ready r = ready("prazo-apagado", 2, 6, 10);
    openMatches(r.f(), false, 72);

    deadlines.apply();

    assertThat(matchState(r.match())).isEqualTo("OPEN -");
    assertThat(bookingStatus(r.booking())).isEqualTo("CONFIRMED");
  }

  @Test
  void raisingTheHoursNeverCancelsInBulk() {
    Ready r = ready("prazo-subir", 2, 6, 10);
    String url = "/api/v1/municipalities/" + r.f().slug();
    String manager = TestTokens.manager(r.f().slug());
    Api.Response raised =
        api()
            .put(url + "/open-match-policy", Map.of("enabled", true, "cancelHours", 72))
            .ifMatch(api().get(url).token(manager).send().etagVersion())
            .token(manager)
            .send();
    assertThat(raised.status()).as(raised.toString()).isEqualTo(200);

    deadlines.apply();

    assertThat(matchState(r.match()))
        .as("o seu prazo novo xa quedou atrás ao cambiar: xógase coma se se cumprise")
        .isEqualTo("OPEN -");
    assertThat(bookingStatus(r.booking())).isEqualTo("CONFIRMED");
  }
}
