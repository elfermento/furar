// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web;

import gal.furar.booking.ActivityPlan;
import gal.furar.event.adapter.in.web.dto.EventAttendanceResponse;
import gal.furar.event.adapter.in.web.dto.EventAttendanceSummaryResponse;
import gal.furar.event.adapter.in.web.dto.EventCoverResponse;
import gal.furar.event.adapter.in.web.dto.EventDataResponse;
import gal.furar.event.adapter.in.web.dto.EventDependentResponse;
import gal.furar.event.adapter.in.web.dto.EventFacilityResponse;
import gal.furar.event.adapter.in.web.dto.EventKindValue;
import gal.furar.event.adapter.in.web.dto.EventNextSessionResponse;
import gal.furar.event.adapter.in.web.dto.EventPaymentStatusValue;
import gal.furar.event.adapter.in.web.dto.EventRegistrationResponse;
import gal.furar.event.adapter.in.web.dto.EventRegistrationStatusValue;
import gal.furar.event.adapter.in.web.dto.EventRequest;
import gal.furar.event.adapter.in.web.dto.EventResponse;
import gal.furar.event.adapter.in.web.dto.EventScheduleRequest;
import gal.furar.event.adapter.in.web.dto.EventScheduleResponse;
import gal.furar.event.adapter.in.web.dto.EventSessionResponse;
import gal.furar.event.adapter.in.web.dto.EventSessionsPreviewResponse;
import gal.furar.event.adapter.in.web.dto.EventTextResponse;
import gal.furar.event.adapter.in.web.dto.EventTodaySessionResponse;
import gal.furar.event.adapter.in.web.dto.MyEventRegistrationResponse;
import gal.furar.event.adapter.in.web.dto.PricePeriodValue;
import gal.furar.event.adapter.in.web.dto.PublicEventResponse;
import gal.furar.event.application.port.in.EventAttendanceUseCase;
import gal.furar.event.application.port.in.EventCommand;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.RegistrationView;
import gal.furar.event.application.port.in.RestoreEventCommand;
import gal.furar.event.application.port.in.SessionView;
import gal.furar.event.domain.Dependent;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventDetails;
import gal.furar.event.domain.EventKind;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventSession;
import gal.furar.event.domain.EventText;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.PricePeriod;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.WaitlistNotice;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilitySummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** DTO ↔ comandos e vistas, a man. */
final class EventWebMapper {

  private EventWebMapper() {}

  static EventCommand toCommand(EventRequest r) {
    return new EventCommand(
        r.nameGl(),
        r.nameEs(),
        r.descriptionGl(),
        r.descriptionEs(),
        r.kind() == null ? null : EventKind.valueOf(r.kind().name()),
        r.startsAt(),
        r.facilityId(),
        r.place(),
        r.schedule() == null ? null : schedule(r.schedule()),
        r.capacity(),
        r.priceCents(),
        r.pricePeriod() == null ? null : PricePeriod.valueOf(r.pricePeriod().name()),
        r.registrationDeadline(),
        r.published(),
        r.approval(),
        r.birthYearFrom(),
        r.birthYearTo());
  }

  static EventCommand.ScheduleCommand schedule(EventScheduleRequest s) {
    List<LocalDate> skip = s.skip();
    return new EventCommand.ScheduleCommand(
        s.useId(),
        s.partId(),
        s.weekdays(),
        s.startMinute(),
        s.endMinute(),
        s.firstDate(),
        s.lastDate(),
        skip == null ? Set.of() : new TreeSet<>(skip));
  }

  static EventResponse toResponse(EventView view) {
    Event e = view.event();
    EventDetails d = e.details();
    Long version = e.version();
    return new EventResponse(
        e.id().value(),
        text(d.name()),
        d.description() == null ? null : text(d.description()),
        EventKindValue.valueOf(d.kind().name()),
        d.startsAt(),
        d.endsAt(),
        facility(view.facility()),
        d.place().other(),
        schedule(e),
        view.useName(),
        view.partName(),
        d.capacity(),
        d.priceCents(),
        PricePeriodValue.valueOf(d.pricePeriod().name()),
        d.registrationDeadline(),
        d.published(),
        d.approval(),
        d.birthYears().from(),
        d.birthYears().to(),
        e.cancelledAt(),
        view.confirmed(),
        view.pending(),
        view.notified(),
        view.waiting(),
        version == null ? 0 : version);
  }

  static PublicEventResponse toPublic(EventView view, String slug, Instant now) {
    Event e = view.event();
    EventDetails d = e.details();
    return new PublicEventResponse(
        e.id().value(),
        text(d.name()),
        d.description() == null ? null : text(d.description()),
        EventKindValue.valueOf(d.kind().name()),
        d.startsAt(),
        d.endsAt(),
        facility(view.facility()),
        d.place().other(),
        schedule(e),
        view.useName(),
        view.partName(),
        view.nextSessions().isEmpty() ? null : next(view.nextSessions().getFirst()),
        d.capacity(),
        view.free(),
        d.priceCents(),
        PricePeriodValue.valueOf(d.pricePeriod().name()),
        d.registrationDeadline(),
        d.approval(),
        d.birthYears().from(),
        d.birthYears().to(),
        view.open(now),
        view.waitlistOpen(now),
        view.waitlisted(),
        cover(view.cover(), slug));
  }

  static MyEventRegistrationResponse mine(RegistrationView view, String slug, Instant now) {
    Registration r = view.registration();
    WaitlistNotice notice = r.notice();
    return new MyEventRegistrationResponse(
        EventRegistrationStatusValue.valueOf(r.status().name()),
        payment(r.paymentFor(view.event().event())),
        dependent(r.dependent()),
        view.position(),
        notice == null ? null : notice.notifiedAt(),
        notice == null ? null : notice.exclusiveUntil(),
        r.coveredAt() != null,
        view.event().nextSessions().stream().map(EventWebMapper::next).toList(),
        toPublic(view.event(), slug, now));
  }

  static MyEventRegistrationResponse none(EventView view, String slug, Instant now) {
    return new MyEventRegistrationResponse(
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        view.nextSessions().stream().map(EventWebMapper::next).toList(),
        toPublic(view, slug, now));
  }

  static EventRegistrationResponse registration(
      Registration r, Event event, @Nullable Integer position) {
    WaitlistNotice notice = r.notice();
    Dependent dependent = r.dependent();
    return new EventRegistrationResponse(
        r.id().value(),
        EventRegistrationStatusValue.valueOf(r.status().name()),
        r.participant().name(),
        r.participant().email(),
        dependent == null ? null : dependent.name(),
        r.birthDate() == null ? r.birthYear() : Integer.valueOf(r.birthDate().getYear()),
        r.birthDate(),
        r.confirmed() ? payment(r.paymentFor(event)) : null,
        position,
        notice == null ? null : notice.exclusiveUntil(),
        r.createdAt(),
        r.decidedAt());
  }

  static EventSessionsPreviewResponse preview(ActivityPlan plan) {
    List<ActivityPlan.Session> sessions = plan.sessions();
    return new EventSessionsPreviewResponse(
        sessions.size(),
        sessions.isEmpty() ? null : sessions.getFirst().start(),
        sessions.isEmpty() ? null : sessions.getLast().start(),
        plan.holidays(),
        plan.useOff(),
        plan.closed(),
        plan.conflicts().stream()
            .map(
                c ->
                    new EventSessionsPreviewResponse.ConflictResponse(
                        c.date(),
                        c.start(),
                        c.end(),
                        c.kind().name(),
                        c.bookingId(),
                        c.name(),
                        c.label()))
            .toList());
  }

  static EventNextSessionResponse next(EventSession s) {
    return new EventNextSessionResponse(s.date(), s.startsAt(), s.endsAt());
  }

  static EventSessionResponse session(SessionView view) {
    EventSession s = view.session();
    return new EventSessionResponse(
        s.id(),
        s.date(),
        s.startsAt(),
        s.endsAt(),
        s.cancelledAt(),
        s.attendanceTakenAt() != null,
        view.present(),
        view.absent());
  }

  static EventAttendanceResponse attendance(EventAttendanceUseCase.SessionAttendance a) {
    EventSession s = a.session();
    return new EventAttendanceResponse(
        s.id(),
        s.date(),
        s.startsAt(),
        s.endsAt(),
        a.taken(),
        a.takenAt(),
        a.takenByName(),
        a.attendees().stream()
            .map(
                m ->
                    new EventAttendanceResponse.AttendeeResponse(
                        m.registration().id().value(),
                        m.registration().participant().name(),
                        m.registration().dependent() == null
                            ? null
                            : m.registration().dependent().name(),
                        m.present()))
            .toList());
  }

  static EventAttendanceSummaryResponse summary(EventAttendanceUseCase.AttendeeSummary s) {
    Registration r = s.registration();
    return new EventAttendanceSummaryResponse(
        r.id().value(),
        r.participant().name(),
        r.dependent() == null ? null : r.dependent().name(),
        s.sessions(),
        s.present(),
        s.absent());
  }

  static EventTodaySessionResponse today(EventAttendanceUseCase.TodaySession t) {
    EventSession s = t.session();
    return new EventTodaySessionResponse(
        t.eventId().value(),
        new EventTextResponse(t.nameGl(), t.nameEs()),
        s.id(),
        s.startsAt(),
        s.endsAt(),
        facility(t.facility()),
        t.place(),
        t.registered(),
        s.attendanceTakenAt() != null,
        t.present(),
        t.absent());
  }

  static EventDataResponse row(EventView view) {
    Event e = view.event();
    EventDetails d = e.details();
    return new EventDataResponse(
        e.id().value(),
        text(d.name()),
        d.description() == null ? null : text(d.description()),
        EventKindValue.valueOf(d.kind().name()),
        d.startsAt(),
        d.endsAt(),
        d.place().facilityId(),
        d.place().other(),
        schedule(e),
        view.useName(),
        view.partName(),
        d.capacity(),
        d.priceCents(),
        PricePeriodValue.valueOf(d.pricePeriod().name()),
        d.registrationDeadline(),
        d.published(),
        d.approval(),
        d.birthYears().from(),
        d.birthYears().to(),
        e.cancelledAt(),
        e.createdAt(),
        e.updatedAt());
  }

  static RestoreEventCommand restore(EventDataResponse row) {
    EventTextResponse description = row.description();
    EventScheduleResponse s = row.schedule();
    return new RestoreEventCommand(
        row.id(),
        new EventCommand(
            row.name().gl(),
            row.name().es(),
            description == null ? null : description.gl(),
            description == null ? null : description.es(),
            row.kind() == null ? EventKind.ONE_DAY : EventKind.valueOf(row.kind().name()),
            row.startsAt(),
            row.facilityId(),
            row.place(),
            s == null
                ? null
                : new EventCommand.ScheduleCommand(
                    s.useId(),
                    s.partId(),
                    s.weekdays(),
                    s.startMinute(),
                    s.endMinute(),
                    s.firstDate(),
                    s.lastDate(),
                    Set.of()),
            row.capacity(),
            row.priceCents(),
            row.pricePeriod() == null ? null : PricePeriod.valueOf(row.pricePeriod().name()),
            row.registrationDeadline(),
            row.published(),
            row.approval(),
            row.birthYearFrom(),
            row.birthYearTo()),
        row.endsAt(),
        s == null ? null : s.endedFrom(),
        row.cancelledAt(),
        row.createdAt(),
        row.updatedAt());
  }

  static @Nullable EventPaymentStatusValue payment(@Nullable PaymentStatus status) {
    return status == null ? null : EventPaymentStatusValue.valueOf(status.name());
  }

  private static @Nullable EventScheduleResponse schedule(Event e) {
    EventSchedule s = e.details().schedule();
    if (s == null) {
      return null;
    }
    return new EventScheduleResponse(
        s.useId(),
        s.partId(),
        s.isoWeekdays(),
        s.startMinute(),
        s.endMinute(),
        s.firstDate(),
        s.lastDate(),
        e.endedFrom());
  }

  private static @Nullable EventDependentResponse dependent(@Nullable Dependent d) {
    return d == null ? null : new EventDependentResponse(d.id(), d.name());
  }

  private static EventTextResponse text(EventText text) {
    return new EventTextResponse(text.gl(), text.es());
  }

  private static @Nullable EventFacilityResponse facility(@Nullable FacilitySummary f) {
    if (f == null) {
      return null;
    }
    FacilitySummary.Address address = f.address();
    return new EventFacilityResponse(f.id(), f.name(), address == null ? null : address.oneLine());
  }

  /** A portada da instalación ou, sen ela, o debuxo xenérico (decorativo). */
  private static EventCoverResponse cover(@Nullable FacilityCover cover, String slug) {
    if (cover == null) {
      String base = "/api/v1/municipalities/" + slug + "/drawings/generic/";
      return new EventCoverResponse(base + "large", base + "thumb", new EventTextResponse("", ""));
    }
    return new EventCoverResponse(
        cover.large(), cover.thumb(), new EventTextResponse(cover.alt().gl(), cover.alt().es()));
  }
}
