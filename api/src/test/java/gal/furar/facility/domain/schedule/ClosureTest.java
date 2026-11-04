// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.domain.FacilityId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Próximos peches da ficha pública (maqueta 54): excepcións pechadas, festivos que pechan e
 * bloqueos; días seguidos co mesmo motivo xúntanse.
 */
class ClosureTest {

  static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
  static final FacilityId FACILITY = FacilityId.newId();
  static final LocalDate FROM = LocalDate.parse("2026-11-03");
  static final LocalDate TO = LocalDate.parse("2027-01-02");

  static TimeRange r(int start, int end) {
    return new TimeRange(start, end);
  }

  /** Todos os días 9:00–21:00; festivos pechados (como o «Todo o ano» do asistente), sen fin. */
  static Season season() {
    SeasonSpec spec = new SeasonSpec("Todo o ano", LocalDate.parse("2026-01-01"), null);
    List<DayHoursSpec> week =
        List.of(ScheduleDay.values()).stream()
            .filter(d -> d != ScheduleDay.HOLIDAYS)
            .map(d -> new DayHoursSpec(d, List.of(r(540, 1260))))
            .toList();
    return Season.create(SeasonId.newId(), FACILITY, spec, null, NOW)
        .update(spec, WeekHours.of(week), NOW);
  }

  static PublicHoliday holiday(String date, String gl) {
    return new PublicHoliday(LocalDate.parse(date), gl, gl, "BOE");
  }

  static ScheduleException exception(String date, Integer start, Integer end, String reason) {
    return ScheduleException.create(
        ScheduleExceptionId.newId(),
        FACILITY,
        new ExceptionSpec(LocalDate.parse(date), start != null, start, end, reason),
        NOW);
  }

  static Block block(String date, int start, int end, String reason) {
    return Block.create(
        BlockId.newId(),
        FACILITY,
        new BlockSpec(LocalDate.parse(date), start, end, reason),
        "xestor",
        NOW);
  }

  static List<Closure> closures(
      List<PublicHoliday> holidays, List<ScheduleException> exceptions, List<Block> blocks) {
    Set<LocalDate> dates =
        holidays.stream().map(PublicHoliday::date).collect(Collectors.toUnmodifiableSet());
    OpeningHours hours = new OpeningHours(List.of(season()), exceptions, dates);
    return Closure.between(hours, holidays, exceptions, blocks, FROM, TO);
  }

  @Test
  void nothingClosedMeansNoClosures() {
    assertThat(closures(List.of(), List.of(), List.of())).isEmpty();
  }

  @Test
  void closedExceptionsHolidaysAndBlocksInDateOrder() {
    PublicHoliday christmas = holiday("2026-12-25", "Natividade do Señor");
    List<Closure> result =
        closures(
            List.of(christmas, holiday("2026-12-08", "Inmaculada Concepción")),
            List.of(
                exception("2026-12-06", null, null, "Día da Constitución"),
                exception("2026-12-08", 600, 840, "Aberto pola mañá"),
                exception("2026-11-20", 600, 840, null)),
            List.of(
                block("2026-12-12", 0, 1440, "Torneo de Nadal"),
                block("2026-12-13", 0, 1440, "Torneo de Nadal"),
                block("2026-12-14", 0, 1440, "Torneo de Nadal"),
                block("2026-11-10", 600, 720, "Mantemento")));
    assertThat(result)
        .containsExactly(
            new Closure(
                LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-11-10"),
                r(600, 720),
                "Mantemento",
                null),
            new Closure(
                LocalDate.parse("2026-12-06"),
                LocalDate.parse("2026-12-06"),
                null,
                "Día da Constitución",
                null),
            new Closure(
                LocalDate.parse("2026-12-12"),
                LocalDate.parse("2026-12-14"),
                null,
                "Torneo de Nadal",
                null),
            new Closure(
                LocalDate.parse("2026-12-25"),
                LocalDate.parse("2026-12-25"),
                null,
                null,
                christmas));
  }

  @Test
  void aHolidayThatKeepsItsHoursIsNotAClosure() {
    SeasonSpec spec = new SeasonSpec("Todo o ano", LocalDate.parse("2026-01-01"), null);
    Season openOnHolidays =
        Season.create(SeasonId.newId(), FACILITY, spec, null, NOW)
            .update(
                spec,
                WeekHours.of(
                    List.of(
                        new DayHoursSpec(ScheduleDay.FRIDAY, List.of(r(540, 1260))),
                        new DayHoursSpec(ScheduleDay.HOLIDAYS, List.of(r(600, 780))))),
                NOW);
    PublicHoliday christmas = holiday("2026-12-25", "Natividade do Señor");
    OpeningHours hours =
        new OpeningHours(List.of(openOnHolidays), List.of(), Set.of(christmas.date()));
    assertThat(Closure.between(hours, List.of(christmas), List.of(), List.of(), FROM, TO))
        .isEmpty();
  }

  @Test
  void aBlockOnADayAlreadyClosedIsNotRepeated() {
    List<Closure> result =
        closures(
            List.of(),
            List.of(exception("2026-12-06", null, null, null)),
            List.of(block("2026-12-06", 0, 1440, "Torneo")));
    assertThat(result)
        .containsExactly(
            new Closure(
                LocalDate.parse("2026-12-06"), LocalDate.parse("2026-12-06"), null, null, null));
  }
}
