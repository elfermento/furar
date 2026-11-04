// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.OpeningHours.Slot;
import gal.furar.facility.domain.schedule.Season.DateSpan;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Horario resolto: prioridade excepción &gt; festivo &gt; semana, tramos en instantes reais nos
 * cambios de hora (29 de marzo e 25 de outubro de 2026), días sen tempada, horas perdidas (P6) e
 * bloqueos restados (D1-16).
 */
class OpeningHoursTest {

  static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
  static final FacilityId FACILITY = FacilityId.newId();
  static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

  static TimeRange r(int start, int end) {
    return new TimeRange(start, end);
  }

  /** Luns a sábado 8:00–22:00, domingo 9:00–14:00 e festivos 10:00–13:00. */
  static Season season(String from, String to) {
    SeasonSpec spec = new SeasonSpec("Inverno", LocalDate.parse(from), LocalDate.parse(to));
    Season empty = Season.create(SeasonId.newId(), FACILITY, spec, null, NOW);
    List<DayHoursSpec> week =
        List.of(
            new DayHoursSpec(ScheduleDay.MONDAY, List.of(r(480, 1320))),
            new DayHoursSpec(ScheduleDay.TUESDAY, List.of(r(480, 1320))),
            new DayHoursSpec(ScheduleDay.WEDNESDAY, List.of(r(480, 1320))),
            new DayHoursSpec(ScheduleDay.THURSDAY, List.of(r(480, 1320))),
            new DayHoursSpec(ScheduleDay.FRIDAY, List.of(r(480, 1320))),
            new DayHoursSpec(ScheduleDay.SATURDAY, List.of(r(480, 1320))),
            new DayHoursSpec(ScheduleDay.SUNDAY, List.of(r(540, 840))),
            new DayHoursSpec(ScheduleDay.HOLIDAYS, List.of(r(600, 780))));
    return empty.update(spec, WeekHours.of(week), NOW);
  }

  static ScheduleException exception(String date, Integer start, Integer end) {
    return ScheduleException.create(
        ScheduleExceptionId.newId(),
        FACILITY,
        new ExceptionSpec(LocalDate.parse(date), start != null, start, end, null),
        NOW);
  }

  static Season wholeDays(String from, String to) {
    SeasonSpec spec = new SeasonSpec("Todo", LocalDate.parse(from), LocalDate.parse(to));
    Season empty = Season.create(SeasonId.newId(), FACILITY, spec, null, NOW);
    List<DayHoursSpec> week =
        Arrays.stream(ScheduleDay.values())
            .map(d -> new DayHoursSpec(d, List.of(r(0, 1440))))
            .toList();
    return empty.update(spec, WeekHours.of(week), NOW);
  }

  @Test
  void exceptionBeatsHolidayAndHolidayBeatsTheWeek() {
    Season winter = season("2026-09-01", "2027-06-30");
    LocalDate christmas = LocalDate.parse("2026-12-25"); // venres e festivo
    LocalDate christmasEve = LocalDate.parse("2026-12-24"); // xoves
    LocalDate newYearsEve = LocalDate.parse("2026-12-31"); // xoves
    OpeningHours hours =
        new OpeningHours(
            List.of(winter),
            List.of(exception("2026-12-24", 540, 840), exception("2026-12-31", null, null)),
            Set.of(christmas));

    assertThat(hours.on(LocalDate.parse("2026-12-23"))).containsExactly(r(480, 1320));
    assertThat(hours.on(christmas)).containsExactly(r(600, 780));
    assertThat(hours.on(christmasEve)).containsExactly(r(540, 840));
    assertThat(hours.on(newYearsEve)).isEmpty();
    assertThat(hours.withoutException(newYearsEve)).containsExactly(r(480, 1320));

    // Unha excepción nun festivo manda sobre a fila de festivos.
    OpeningHours onHoliday =
        new OpeningHours(
            List.of(winter), List.of(exception("2026-12-25", null, null)), Set.of(christmas));
    assertThat(onHoliday.on(christmas)).isEmpty();
  }

  @Test
  void withoutSeasonTheDayIsClosedUnlessThereIsAnException() {
    OpeningHours hours =
        new OpeningHours(
            List.of(season("2026-09-01", "2026-12-31")),
            List.of(exception("2027-01-02", 600, 720)),
            Set.of(LocalDate.parse("2027-01-01")));
    assertThat(hours.on(LocalDate.parse("2027-01-01"))).isEmpty();
    assertThat(hours.on(LocalDate.parse("2027-01-03"))).isEmpty();
    assertThat(hours.on(LocalDate.parse("2027-01-02"))).containsExactly(r(600, 720));
  }

  /** 29 de marzo de 2026: ás 2:00 pasa a ser as 3:00 (o día dura 23 horas). */
  @Test
  void springForwardDayLastsTwentyThreeHours() {
    LocalDate day = LocalDate.parse("2026-03-29");
    OpeningHours hours =
        new OpeningHours(List.of(wholeDays("2026-03-01", "2026-04-30")), List.of(), Set.of());
    List<Slot> slots = hours.slots(day, day, MADRID);
    assertThat(slots)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.start()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
              assertThat(s.end()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
              assertThat(Duration.between(s.start(), s.end())).isEqualTo(Duration.ofHours(23));
            });

    // 1:00–3:00 de reloxo é unha hora real; 2:00–2:45 non existe e non sae.
    OpeningHours gap =
        new OpeningHours(List.of(), List.of(exception("2026-03-29", 60, 180)), Set.of());
    assertThat(gap.slots(day, day, MADRID))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.start()).isEqualTo(Instant.parse("2026-03-29T00:00:00Z"));
              assertThat(s.end()).isEqualTo(Instant.parse("2026-03-29T01:00:00Z"));
            });
    OpeningHours missing =
        new OpeningHours(List.of(), List.of(exception("2026-03-29", 120, 165)), Set.of());
    assertThat(missing.slots(day, day, MADRID)).isEmpty();
  }

  /** 25 de outubro de 2026: ás 3:00 volven ser as 2:00 (o día dura 25 horas). */
  @Test
  void fallBackDayLastsTwentyFiveHours() {
    LocalDate day = LocalDate.parse("2026-10-25");
    OpeningHours hours =
        new OpeningHours(List.of(wholeDays("2026-10-01", "2026-10-31")), List.of(), Set.of());
    List<Slot> slots = hours.slots(day.minusDays(1), day.plusDays(1), MADRID);
    assertThat(slots).hasSize(3);
    Slot sunday = slots.get(1);
    assertThat(sunday.date()).isEqualTo(day);
    assertThat(sunday.start()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
    assertThat(sunday.end()).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
    assertThat(Duration.between(sunday.start(), sunday.end())).isEqualTo(Duration.ofHours(25));
    // Os tres días seguen sen ocos entre eles.
    assertThat(slots.get(0).end()).isEqualTo(sunday.start());
    assertThat(sunday.end()).isEqualTo(slots.get(2).start());

    // 1:00–3:00 de reloxo son tres horas reais: a hora das 2 repítese.
    OpeningHours twice =
        new OpeningHours(List.of(), List.of(exception("2026-10-25", 60, 180)), Set.of());
    assertThat(twice.slots(day, day, MADRID))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.start()).isEqualTo(Instant.parse("2026-10-24T23:00:00Z"));
              assertThat(s.end()).isEqualTo(Instant.parse("2026-10-25T02:00:00Z"));
            });
  }

  @Test
  void gapsAreTheDaysWithoutSeason() {
    OpeningHours hours =
        new OpeningHours(
            List.of(season("2026-11-01", "2026-11-30"), season("2026-12-05", "2027-06-30")),
            List.of(exception("2026-12-02", 600, 720)),
            Set.of());
    assertThat(hours.gaps(LocalDate.parse("2026-10-25"), LocalDate.parse("2027-10-24")))
        .containsExactly(
            new DateSpan(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-31")),
            new DateSpan(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-04")),
            new DateSpan(LocalDate.parse("2027-07-01"), LocalDate.parse("2027-10-24")));
    assertThat(
            new OpeningHours(List.of(season("2026-01-01", "2027-12-31")), List.of(), Set.of())
                .gaps(LocalDate.parse("2026-10-25"), LocalDate.parse("2027-10-24")))
        .isEmpty();
  }

  @Test
  void aSeasonWithoutEndLeavesNoGapsAfterItsStart() {
    SeasonSpec spec = new SeasonSpec("Todo o ano", LocalDate.parse("2026-01-01"), null);
    Season always = Season.create(SeasonId.newId(), FACILITY, spec, null, NOW);
    OpeningHours hours = new OpeningHours(List.of(always), List.of(), Set.of());
    assertThat(hours.gaps(LocalDate.parse("2026-10-25"), LocalDate.parse("2027-10-24"))).isEmpty();
    assertThat(hours.gaps(LocalDate.parse("2025-12-30"), LocalDate.parse("2026-01-02")))
        .containsExactly(
            new DateSpan(LocalDate.parse("2025-12-30"), LocalDate.parse("2025-12-31")));
  }

  @Test
  void losesHoursWhenAnyOpenMinuteIsLeftOut() {
    assertThat(OpeningHours.losesHours(List.of(r(480, 1320)), List.of(r(480, 1320)))).isFalse();
    assertThat(OpeningHours.losesHours(List.of(r(480, 1320)), List.of(r(420, 1440)))).isFalse();
    assertThat(OpeningHours.losesHours(List.of(r(480, 1320)), List.of(r(480, 900), r(900, 1320))))
        .isFalse();
    assertThat(OpeningHours.losesHours(List.of(r(480, 1320)), List.of(r(540, 1320)))).isTrue();
    assertThat(OpeningHours.losesHours(List.of(r(480, 1320)), List.of(r(480, 840), r(900, 1320))))
        .isTrue();
    assertThat(OpeningHours.losesHours(List.of(r(480, 1320)), List.of())).isTrue();
    assertThat(OpeningHours.losesHours(List.of(), List.of(r(480, 1320)))).isFalse();
  }

  static Block block(String date, int start, int end) {
    return Block.create(
        BlockId.newId(),
        FACILITY,
        new BlockSpec(LocalDate.parse(date), start, end, null),
        "manager-1",
        NOW);
  }

  @Test
  void minusCutsTheBlockedMinutes() {
    assertThat(OpeningHours.minus(List.of(r(480, 1320)), List.of(r(600, 720))))
        .containsExactly(r(480, 600), r(720, 1320));
    // Bloqueos que se pisan entre eles, fóra do tramo ou que o cobren enteiro.
    assertThat(
            OpeningHours.minus(
                List.of(r(480, 840), r(960, 1320)),
                List.of(r(420, 540), r(510, 600), r(1320, 1440), r(960, 1320))))
        .containsExactly(r(600, 840));
    assertThat(OpeningHours.minus(List.of(r(480, 1320)), List.of())).containsExactly(r(480, 1320));
    assertThat(OpeningHours.minus(List.of(r(480, 1320)), List.of(r(0, 1440)))).isEmpty();
  }

  @Test
  void slotsLeaveOutTheBlocksOfEachDay() {
    OpeningHours hours =
        new OpeningHours(List.of(season("2026-09-01", "2027-06-30")), List.of(), Set.of());
    // Martes 3 de novembro, 8:00–22:00, bloqueado de 8:00 a 12:00 e de 14:00 a 15:00.
    List<Slot> slots =
        hours.slots(
            LocalDate.parse("2026-11-03"),
            LocalDate.parse("2026-11-04"),
            MADRID,
            List.of(block("2026-11-03", 480, 720), block("2026-11-03", 840, 900)));
    assertThat(slots)
        .extracting(Slot::range)
        .containsExactly(r(720, 840), r(900, 1320), r(480, 1320));
    assertThat(slots.get(0).start()).isEqualTo(Instant.parse("2026-11-03T11:00:00Z"));
    assertThat(slots.get(2).date()).isEqualTo(LocalDate.parse("2026-11-04"));
  }

  /** O bloqueo de 2:00 a 3:00 do 25 de outubro quita as dúas horas reais que van de 2:00 a 3:00. */
  @Test
  void blocksAreSubtractedAcrossTheAutumnChange() {
    OpeningHours hours =
        new OpeningHours(List.of(wholeDays("2026-10-01", "2026-10-31")), List.of(), Set.of());
    Block night = block("2026-10-25", 120, 180);
    List<Slot> slots =
        hours.slots(
            LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-25"), MADRID, List.of(night));
    assertThat(slots).hasSize(2);
    assertThat(slots.get(0).end()).isEqualTo(night.start());
    assertThat(slots.get(1).start()).isEqualTo(night.end());
    assertThat(Duration.between(slots.get(0).start(), slots.get(1).end()))
        .isEqualTo(Duration.ofHours(25));
  }
}
