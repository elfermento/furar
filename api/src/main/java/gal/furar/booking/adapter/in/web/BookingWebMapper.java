// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.StaffAccounts;
import gal.furar.booking.adapter.in.web.dto.AvailableSlotResponse;
import gal.furar.booking.adapter.in.web.dto.BookedByResponse;
import gal.furar.booking.adapter.in.web.dto.BookingCalendarResponse;
import gal.furar.booking.adapter.in.web.dto.BookingDayCountResponse;
import gal.furar.booking.adapter.in.web.dto.BookingHourCountResponse;
import gal.furar.booking.adapter.in.web.dto.BookingMailKindValue;
import gal.furar.booking.adapter.in.web.dto.BookingMailResponse;
import gal.furar.booking.adapter.in.web.dto.BookingOpeningResponse;
import gal.furar.booking.adapter.in.web.dto.BookingPriceKindValue;
import gal.furar.booking.adapter.in.web.dto.BookingPriceResponse;
import gal.furar.booking.adapter.in.web.dto.BookingRentalModeValue;
import gal.furar.booking.adapter.in.web.dto.BookingStatusValue;
import gal.furar.booking.adapter.in.web.dto.BookingTabResponse;
import gal.furar.booking.adapter.in.web.dto.BookingsOverviewResponse;
import gal.furar.booking.adapter.in.web.dto.CalendarViewValue;
import gal.furar.booking.adapter.in.web.dto.CancelChannelValue;
import gal.furar.booking.adapter.in.web.dto.CoverImageResponse;
import gal.furar.booking.adapter.in.web.dto.CoverKindValue;
import gal.furar.booking.adapter.in.web.dto.CoverTextResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingDetailResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingResponse;
import gal.furar.booking.adapter.in.web.dto.MyBookingResponse;
import gal.furar.booking.adapter.in.web.dto.PaymentStatusValue;
import gal.furar.booking.adapter.in.web.dto.QrResponse;
import gal.furar.booking.adapter.in.web.dto.StaffRoleValue;
import gal.furar.booking.application.port.in.BookingCalendar;
import gal.furar.booking.application.port.in.BookingsOverview;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.FreeSlot;
import gal.furar.booking.domain.Holder;
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
        version(view));
  }

  private static @Nullable CoverImageResponse cover(@Nullable FacilityCover cover) {
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
        version(b));
  }

  private static @Nullable BookedByResponse bookedBy(ManagedBooking view) {
    StaffAccounts.Member member = view.bookedBy();
    return member == null
        ? null
        : new BookedByResponse(member.name(), StaffRoleValue.valueOf(member.role().name()));
  }

  /**
   * As canles do aviso ao cancelar (P12): o correo, se a persoa o ten; o aviso na web, se ten conta
   * onde velo.
   */
  private static List<CancelChannelValue> cancelChannels(Booking b) {
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
        b.holder() != null && b.holder().hasAccount(),
        bookedBy(view),
        view.lastMail() == null
            ? null
            : new BookingMailResponse(
                BookingMailKindValue.valueOf(view.lastMail().kind().name()), view.lastMail().at()),
        version(b));
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
