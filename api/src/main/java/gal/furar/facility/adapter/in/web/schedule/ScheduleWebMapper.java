// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.adapter.in.web.dto.BookingDecisionRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.BlockRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.BlockResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.DateSpanResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.DayHoursRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.DayHoursResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.HolidayNameResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.PublicHolidayResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleDayValue;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleExceptionRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleExceptionResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.SeasonResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.TimeRangeRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.TimeRangeResponse;
import gal.furar.facility.application.port.in.schedule.ScheduleView;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockSpec;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.TimeRange;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** DTO ↔ dominio do horario, a man. Tamén o usa a sección de portabilidade do módulo. */
public final class ScheduleWebMapper {

  private ScheduleWebMapper() {}

  static List<DayHoursSpec> week(List<DayHoursRequest> rows) {
    return rows.stream()
        .map(
            r ->
                new DayHoursSpec(
                    r.day() == null ? null : ScheduleDay.valueOf(r.day().name()),
                    r.intervals() == null
                        ? List.of()
                        : r.intervals().stream().map(ScheduleWebMapper::range).toList()))
        .toList();
  }

  private static TimeRange range(TimeRangeRequest r) {
    return new TimeRange(r.startMinute(), r.endMinute());
  }

  static List<BookingDecision> decisions(@Nullable List<BookingDecisionRequest> decisions) {
    return decisions == null
        ? List.of()
        : decisions.stream().map(ScheduleWebMapper::decision).toList();
  }

  private static BookingDecision decision(BookingDecisionRequest r) {
    Set<BookingDecision.Channel> channels =
        r.channels() == null
            ? Set.of()
            : r.channels().stream()
                .map(c -> BookingDecision.Channel.valueOf(c.name()))
                .collect(Collectors.toUnmodifiableSet());
    return new BookingDecision(
        r.bookingId(), BookingDecision.Action.valueOf(r.action().name()), channels);
  }

  public static SeasonResponse toResponse(Season s) {
    return new SeasonResponse(
        s.id().value(),
        s.facilityId().value(),
        s.name(),
        s.startDate(),
        s.endDate(),
        s.week().days().entrySet().stream()
            .map(
                e ->
                    new DayHoursResponse(
                        ScheduleDayValue.valueOf(e.getKey().name()),
                        e.getValue().stream()
                            .map(r -> new TimeRangeResponse(r.startMinute(), r.endMinute()))
                            .toList()))
            .toList(),
        s.version() == null ? 0 : s.version(),
        s.createdAt(),
        s.updatedAt());
  }

  static ScheduleResponse toResponse(ScheduleView v) {
    SeasonId current = v.currentSeasonId();
    return new ScheduleResponse(
        v.seasons().stream().map(ScheduleWebMapper::toResponse).toList(),
        current == null ? null : current.value(),
        v.year(),
        holidays(v.holidays()),
        v.exceptions().stream().map(ScheduleWebMapper::toResponse).toList(),
        v.today(),
        v.nowMinute(),
        v.gaps().stream().map(g -> new DateSpanResponse(g.from(), g.to())).toList());
  }

  static ExceptionSpec spec(ScheduleExceptionRequest r) {
    return new ExceptionSpec(
        r.date(), Boolean.TRUE.equals(r.open()), r.startMinute(), r.endMinute(), r.reason());
  }

  public static ScheduleExceptionResponse toResponse(ScheduleException e) {
    TimeRange interval = e.interval();
    return new ScheduleExceptionResponse(
        e.id().value(),
        e.facilityId().value(),
        e.date(),
        e.isOpen(),
        interval == null ? null : interval.startMinute(),
        interval == null ? null : interval.endMinute(),
        e.reason(),
        e.version() == null ? 0 : e.version(),
        e.createdAt(),
        e.updatedAt());
  }

  static BlockSpec spec(BlockRequest r) {
    return new BlockSpec(r.date(), r.startMinute(), r.endMinute(), r.reason());
  }

  static BlockResponse toResponse(Block b) {
    return new BlockResponse(
        b.id().value(),
        b.facilityId().value(),
        b.date(),
        b.interval().startMinute(),
        b.interval().endMinute(),
        b.start(),
        b.end(),
        b.reason(),
        b.version() == null ? 0 : b.version(),
        b.createdAt(),
        b.updatedAt());
  }

  static List<PublicHolidayResponse> holidays(List<PublicHoliday> holidays) {
    return holidays.stream()
        .map(
            h ->
                new PublicHolidayResponse(
                    h.date(), new HolidayNameResponse(h.nameGl(), h.nameEs()), h.source()))
        .toList();
  }
}
