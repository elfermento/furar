// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.StaffAccounts;
import gal.furar.booking.adapter.in.web.dto.AvailableSlotResponse;
import gal.furar.booking.adapter.in.web.dto.BookedByResponse;
import gal.furar.booking.adapter.in.web.dto.BookingActivityResponse;
import gal.furar.booking.adapter.in.web.dto.BookingAttendanceValue;
import gal.furar.booking.adapter.in.web.dto.BookingCalendarResponse;
import gal.furar.booking.adapter.in.web.dto.BookingDayCountResponse;
import gal.furar.booking.adapter.in.web.dto.BookingHourCountResponse;
import gal.furar.booking.adapter.in.web.dto.BookingMailKindValue;
import gal.furar.booking.adapter.in.web.dto.BookingMailResponse;
import gal.furar.booking.adapter.in.web.dto.BookingOpeningResponse;
import gal.furar.booking.adapter.in.web.dto.BookingOrganizationResponse;
import gal.furar.booking.adapter.in.web.dto.BookingParticipantResponse;
import gal.furar.booking.adapter.in.web.dto.BookingParticipantsRequest;
import gal.furar.booking.adapter.in.web.dto.BookingPriceKindValue;
import gal.furar.booking.adapter.in.web.dto.BookingPriceResponse;
import gal.furar.booking.adapter.in.web.dto.BookingRentalModeValue;
import gal.furar.booking.adapter.in.web.dto.BookingSeasonResponse;
import gal.furar.booking.adapter.in.web.dto.BookingStatusValue;
import gal.furar.booking.adapter.in.web.dto.BookingTabResponse;
import gal.furar.booking.adapter.in.web.dto.BookingValidationOutcomeValue;
import gal.furar.booking.adapter.in.web.dto.BookingValidationResponse;
import gal.furar.booking.adapter.in.web.dto.BookingWaitResponse;
import gal.furar.booking.adapter.in.web.dto.BookingsOverviewResponse;
import gal.furar.booking.adapter.in.web.dto.CalendarViewValue;
import gal.furar.booking.adapter.in.web.dto.CancelChannelValue;
import gal.furar.booking.adapter.in.web.dto.CoverImageResponse;
import gal.furar.booking.adapter.in.web.dto.CoverKindValue;
import gal.furar.booking.adapter.in.web.dto.CoverTextResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingDetailResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingResponse;
import gal.furar.booking.adapter.in.web.dto.MyBookingResponse;
import gal.furar.booking.adapter.in.web.dto.NoShowStandingResponse;
import gal.furar.booking.adapter.in.web.dto.ParticipantKindValue;
import gal.furar.booking.adapter.in.web.dto.PaymentStatusValue;
import gal.furar.booking.adapter.in.web.dto.QrResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesPaymentStatusValue;
import gal.furar.booking.adapter.in.web.dto.SimilarSlotReasonValue;
import gal.furar.booking.adapter.in.web.dto.SimilarSlotResponse;
import gal.furar.booking.adapter.in.web.dto.SimilarSlotsResponse;
import gal.furar.booking.adapter.in.web.dto.StaffBookingParticipantsRequest;
import gal.furar.booking.adapter.in.web.dto.StaffRoleValue;
import gal.furar.booking.application.port.in.AttendanceUseCase;
import gal.furar.booking.application.port.in.BookingActivity;
import gal.furar.booking.application.port.in.BookingCalendar;
import gal.furar.booking.application.port.in.BookingOrganization;
import gal.furar.booking.application.port.in.BookingsOverview;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.NoShowStanding;
import gal.furar.booking.application.port.in.ParticipantSelection;
import gal.furar.booking.application.port.in.SimilarSlotsUseCase;
import gal.furar.booking.application.port.in.WaitUseCase;
import gal.furar.booking.domain.Attendance;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.FreeSlot;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.ParticipantKind;
import gal.furar.facility.FacilityCover;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Dominio e vistas das reservas → DTO (A5: a API só fala DTO). */
final class BookingWebMapper {

  private BookingWebMapper() {}

  static AvailableSlotResponse slot(FreeSlot slot) {
    return new AvailableSlotResponse(
        slot.start(),
        slot.startMinute(),
        slot.maxSlots(),
        slot.freeParts().isEmpty() ? null : slot.freeParts(),
        slot.freeSeats());
  }

  static BookingWaitResponse waiting(WaitUseCase.MyWait view) {
    return new BookingWaitResponse(
        view.entry().id(),
        view.entry().facilityId(),
        view.facilityName(),
        cover(view.cover()),
        view.entry().useId(),
        view.useName(),
        view.entry().start(),
        view.entry().end());
  }

  static SimilarSlotsResponse similar(List<SimilarSlotsUseCase.Item> items) {
    return new SimilarSlotsResponse(
        items.stream()
            .map(
                i ->
                    new SimilarSlotResponse(
                        SimilarSlotReasonValue.valueOf(i.slot().reason().name()),
                        i.slot().slot().facilityId(),
                        i.facilityName(),
                        i.facilityAddress(),
                        i.slot().slot().useId(),
                        i.slot().slot().date(),
                        i.slot().slot().start(),
                        i.slot().slot().end(),
                        i.slot().slot().startMinute()))
            .toList());
  }

  static MyBookingResponse mine(MyBooking view) {
    Booking b = view.booking();
    return new MyBookingResponse(
        b.id().value(),
        b.code().value(),
        b.slot().facilityId(),
        view.facilityName(),
        b.slot().useId(),
        view.useName(),
        BookingRentalModeValue.valueOf(b.slot().rentalMode().name()),
        b.slot().partId(),
        view.partName(),
        b.slot().start(),
        b.slot().end(),
        b.slot().slots(),
        BookingStatusValue.valueOf(b.status().name()),
        PaymentStatusValue.valueOf(b.paymentStatus().name()),
        new BookingPriceResponse(
            BookingPriceKindValue.valueOf(b.price().kind().name()), b.price().amountCents()),
        b.cancelUntil(),
        view.canCancel(),
        view.done(),
        b.createdAt(),
        new QrResponse(view.qr().size(), view.qr().path()),
        cover(view.cover()),
        view.customFields().toJson(),
        b.seats(),
        participants(b, view.participants(), false),
        b.attendance() == Attendance.NO_SHOW,
        view.canReview(),
        view.reviewStars(),
        OpenMatchWebMapper.booking(view.openMatch()),
        organization(view.organization()),
        version(view));
  }

  static @Nullable BookingOrganizationResponse organization(
      @Nullable BookingOrganization organization) {
    if (organization == null) {
      return null;
    }
    BookingOrganization.Season s = organization.series();
    return new BookingOrganizationResponse(
        organization.id(),
        organization.name(),
        organization.seriesId(),
        s == null
            ? null
            : new BookingSeasonResponse(
                s.id(),
                s.weekdays(),
                s.startMinute(),
                s.endMinute(),
                s.firstDate(),
                s.lastDate(),
                s.sessions(),
                SeriesPaymentStatusValue.valueOf(s.paymentStatus())));
  }

  /**
   * «Para quen»: quen ten a reserva co seu nome e as persoas a cargo e acompañantes cos seus; o ano
   * de nacemento, só para o persoal. As anonimizadas non saen (queda o número de prazas).
   */
  private static List<BookingParticipantResponse> participants(
      Booking b, List<Participant> participants, boolean withYear) {
    Holder holder = b.holder();
    List<BookingParticipantResponse> list = new ArrayList<>();
    for (Participant p : participants) {
      if (p.kind() == ParticipantKind.HOLDER) {
        if (holder != null) {
          list.add(
              new BookingParticipantResponse(ParticipantKindValue.SELF, holder.name(), null, null));
        }
      } else if (p.name() != null) {
        list.add(
            new BookingParticipantResponse(
                ParticipantKindValue.valueOf(p.kind().name()),
                p.name(),
                withYear ? p.birthYear() : null,
                p.dependentId()));
      }
    }
    return list;
  }

  static @Nullable CoverImageResponse cover(@Nullable FacilityCover cover) {
    if (cover == null) {
      return null;
    }
    return new CoverImageResponse(
        CoverKindValue.valueOf(cover.kind().name()),
        cover.id(),
        text(cover.alt()),
        cover.width(),
        cover.height(),
        cover.large(),
        cover.thumb(),
        cover.uploadedAt(),
        cover.name() == null ? null : text(cover.name()));
  }

  private static CoverTextResponse text(FacilityCover.Text text) {
    return new CoverTextResponse(text.gl(), text.es());
  }

  static ManagedBookingResponse managed(ManagedBooking view) {
    Booking b = view.booking();
    return new ManagedBookingResponse(
        b.id().value(),
        b.code().value(),
        b.holder() == null ? null : b.holder().name(),
        b.slot().facilityId(),
        view.facilityName(),
        b.slot().useId(),
        view.useName(),
        BookingRentalModeValue.valueOf(b.slot().rentalMode().name()),
        b.slot().partId(),
        view.partName(),
        b.slot().start(),
        b.slot().end(),
        b.slot().slots(),
        BookingStatusValue.valueOf(b.status().name()),
        PaymentStatusValue.valueOf(b.paymentStatus().name()),
        price(b),
        view.done(),
        b.createdAt(),
        b.cancelledAt(),
        bookedBy(view),
        b.seats(),
        BookingAttendanceValue.valueOf(b.attendance().name()),
        organization(view.organization()),
        activity(view.activity()),
        version(b));
  }

  private static @Nullable BookedByResponse bookedBy(ManagedBooking view) {
    StaffAccounts.Member member = view.bookedBy();
    return member == null
        ? null
        : new BookedByResponse(member.name(), StaffRoleValue.valueOf(member.role().name()));
  }

  /**
   * As canles do aviso ao cancelar: o correo, se a persoa o ten; o aviso na web, se ten conta onde
   * velo.
   */
  private static List<CancelChannelValue> cancelChannels(Booking b) {
    // A dunha entidade avísase ao correo da entidade (ADR-0039, punto 12); sen web.
    if (b.organizationId() != null) {
      return List.of(CancelChannelValue.EMAIL);
    }
    Holder h = b.holder();
    List<CancelChannelValue> channels = new ArrayList<>();
    if (h == null || h.email() != null) {
      channels.add(CancelChannelValue.EMAIL);
    }
    if (h == null || h.hasAccount()) {
      channels.add(CancelChannelValue.WEB);
    }
    return channels;
  }

  static ManagedBookingDetailResponse detail(ManagedBooking view) {
    Booking b = view.booking();
    boolean cancellable = !b.status().isCancelled();
    return new ManagedBookingDetailResponse(
        b.id().value(),
        b.code().value(),
        b.holder() == null ? null : b.holder().name(),
        b.holder() == null ? null : b.holder().email(),
        b.slot().facilityId(),
        view.facilityName(),
        view.facilityAddress(),
        b.slot().useId(),
        view.useName(),
        BookingRentalModeValue.valueOf(b.slot().rentalMode().name()),
        b.slot().partId(),
        view.partName(),
        b.slot().start(),
        b.slot().end(),
        b.slot().slots(),
        BookingStatusValue.valueOf(b.status().name()),
        PaymentStatusValue.valueOf(b.paymentStatus().name()),
        price(b),
        b.cancelUntil(),
        view.done(),
        b.createdAt(),
        b.cancelledAt(),
        view.paymentTargets().stream()
            .map(p -> PaymentStatusValue.valueOf(p.name()))
            .sorted()
            .toList(),
        cancellable,
        cancellable ? cancelChannels(b) : List.of(),
        view.customFields().toJson(),
        b.holder() == null ? null : b.holder().phone(),
        b.holder() != null && b.bookedBy() == null,
        bookedBy(view),
        view.lastMail() == null
            ? null
            : new BookingMailResponse(
                BookingMailKindValue.valueOf(view.lastMail().kind().name()), view.lastMail().at()),
        b.seats(),
        participants(b, view.participants(), true),
        BookingAttendanceValue.valueOf(b.attendance().name()),
        view.done() && b.attendance() == Attendance.NONE,
        standing(view.noShow()),
        view.blockLifted(),
        OpenMatchWebMapper.booking(view.openMatch()),
        organization(view.organization()),
        activity(view.activity()),
        version(b));
  }

  private static @Nullable BookingActivityResponse activity(@Nullable BookingActivity a) {
    return a == null ? null : new BookingActivityResponse(a.eventId(), a.sessionId());
  }

  private static @Nullable NoShowStandingResponse standing(@Nullable NoShowStanding s) {
    return s == null
        ? null
        : new NoShowStandingResponse(
            s.faults(), s.windowDays(), s.threshold(), s.blockDays(), s.blockedUntil());
  }

  static BookingValidationResponse validation(AttendanceUseCase.Validation v) {
    return new BookingValidationResponse(
        BookingValidationOutcomeValue.valueOf(v.outcome().name()),
        detail(v.booking()),
        v.attendanceAt(),
        v.attendanceBy());
  }

  /** Para quen é, tal como o pide a persoa; nulo se non o di. */
  static @Nullable ParticipantSelection selection(@Nullable BookingParticipantsRequest request) {
    return request == null
        ? null
        : new ParticipantSelection(
            !Boolean.FALSE.equals(request.self()), request.dependentIds(), List.of());
  }

  /** Para quen é, tal como o pide o persoal (cos acompañantes); nulo se non o di. */
  static @Nullable ParticipantSelection selection(
      @Nullable StaffBookingParticipantsRequest request) {
    if (request == null) {
      return null;
    }
    List<ParticipantSelection.Companion> companions =
        request.companions() == null
            ? List.of()
            : request.companions().stream()
                .map(c -> new ParticipantSelection.Companion(c.name().strip(), c.birthYear()))
                .toList();
    return new ParticipantSelection(
        !Boolean.FALSE.equals(request.self()), request.dependentIds(), companions);
  }

  static BookingsOverviewResponse overview(BookingsOverview overview) {
    return new BookingsOverviewResponse(
        overview.latest().stream().map(BookingWebMapper::managed).toList(),
        tab(overview.pendingPayment()),
        tab(overview.toRefund()),
        tab(overview.cancelled()));
  }

  static BookingCalendarResponse calendar(BookingCalendar calendar) {
    return new BookingCalendarResponse(
        CalendarViewValue.valueOf(calendar.view().name()),
        calendar.from(),
        calendar.to(),
        calendar.blocks().stream().map(BookingWebMapper::managed).toList(),
        calendar.days().stream()
            .map(c -> new BookingDayCountResponse(c.date(), c.count()))
            .toList(),
        calendar.hours().stream()
            .map(
                c ->
                    new BookingHourCountResponse(
                        c.date(), Objects.requireNonNull(c.hour()), c.count()))
            .toList(),
        calendar.openings().stream()
            .map(
                o ->
                    new BookingOpeningResponse(
                        o.facilityId(), o.date(), o.startMinute(), o.endMinute()))
            .toList());
  }

  private static BookingTabResponse tab(BookingsOverview.Tab tab) {
    return new BookingTabResponse(
        tab.count(), tab.first().stream().map(BookingWebMapper::managed).toList());
  }

  private static BookingPriceResponse price(Booking b) {
    return new BookingPriceResponse(
        BookingPriceKindValue.valueOf(b.price().kind().name()), b.price().amountCents());
  }

  static long version(Booking booking) {
    Long version = booking.version();
    return version == null ? 0 : version;
  }

  static long version(MyBooking view) {
    Long version = view.booking().version();
    return version == null ? 0 : version;
  }
}
