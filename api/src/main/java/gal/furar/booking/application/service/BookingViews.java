// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.QrEncoder;
import gal.furar.booking.domain.Booking;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CustomFieldValues;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * As reservas cos nomes de agora da instalación, o uso e a parte: unha lectura para as instalacións
 * e outra para os usos de toda a lista, nunca unha por reserva. Para quen as fixo, co QR do código
 * e os campos propios que ve; para o persoal, co que se pode facer e todos os campos propios (unha
 * lectura máis por lista).
 */
@Component
class BookingViews {

  private final FacilityQueries facilities;
  private final QrEncoder qr;
  private final BookingCustomFields customFields;

  BookingViews(FacilityQueries facilities, QrEncoder qr, BookingCustomFields customFields) {
    this.facilities = facilities;
    this.qr = qr;
    this.customFields = customFields;
  }

  MyBooking view(Booking booking, Instant now) {
    return views(List.of(booking), now).getFirst();
  }

  /**
   * Para o correo, fóra dunha petición: sen portada (as súas rutas levan o concello do URL) nin
   * campos propios.
   */
  MyBooking withoutCover(Booking booking, Instant now) {
    return views(List.of(booking), now, false).getFirst();
  }

  List<MyBooking> views(List<Booking> bookings, Instant now) {
    return views(bookings, now, true);
  }

  private List<MyBooking> views(List<Booking> bookings, Instant now, boolean withCover) {
    if (bookings.isEmpty()) {
      return List.of();
    }
    Names names = names(bookings);
    Map<UUID, FacilityCover> covers =
        withCover
            ? facilities
                .covers(bookings.stream().map(b -> b.slot().facilityId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(FacilityCover::facilityId, Function.identity()))
            : Map.of();
    Map<UUID, CustomFieldValues> fields =
        withCover ? customFields.forHolder(ids(bookings)) : Map.of();
    return bookings.stream()
        .map(
            b ->
                new MyBooking(
                    b,
                    names.facility(b),
                    names.address(b),
                    names.use(b),
                    names.part(b),
                    qr.encode(b.code().value()),
                    b.canCancel(now),
                    b.isDone(now),
                    covers.get(b.slot().facilityId()),
                    fields.getOrDefault(b.id().value(), CustomFieldValues.empty())))
        .toList();
  }

  ManagedBooking managed(Booking booking, Instant now) {
    return managed(List.of(booking), now).getFirst();
  }

  List<ManagedBooking> managed(List<Booking> bookings, Instant now) {
    if (bookings.isEmpty()) {
      return List.of();
    }
    Names names = names(bookings);
    Map<UUID, CustomFieldValues> fields = customFields.forStaff(ids(bookings));
    return bookings.stream()
        .map(
            b ->
                new ManagedBooking(
                    b,
                    names.facility(b),
                    names.address(b),
                    names.use(b),
                    names.part(b),
                    b.isDone(now),
                    b.paymentTargets(),
                    fields.getOrDefault(b.id().value(), CustomFieldValues.empty())))
        .toList();
  }

  private static List<UUID> ids(List<Booking> bookings) {
    return bookings.stream().map(b -> b.id().value()).toList();
  }

  private Names names(List<Booking> bookings) {
    Map<UUID, FacilitySummary> byFacility =
        facilities
            .summary(bookings.stream().map(b -> b.slot().facilityId()).distinct().toList())
            .stream()
            .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    Map<UUID, UseSummary> byUse =
        facilities
            .usesById(bookings.stream().map(b -> b.slot().useId()).distinct().toList())
            .stream()
            .collect(Collectors.toMap(UseSummary::id, Function.identity()));
    return new Names(byFacility, byUse);
  }

  /** Os nomes da lista; o que xa non existe sae baleiro. */
  private record Names(Map<UUID, FacilitySummary> facilities, Map<UUID, UseSummary> uses) {

    String facility(Booking b) {
      FacilitySummary f = facilities.get(b.slot().facilityId());
      return f == null ? "" : f.name();
    }

    @Nullable String address(Booking b) {
      FacilitySummary f = facilities.get(b.slot().facilityId());
      FacilitySummary.Address a = f == null ? null : f.address();
      return a == null ? null : a.oneLine();
    }

    String use(Booking b) {
      UseSummary u = uses.get(b.slot().useId());
      return u == null ? "" : u.name();
    }

    @Nullable String part(Booking b) {
      UseSummary u = uses.get(b.slot().useId());
      UUID partId = b.slot().partId();
      if (u == null || partId == null) {
        return null;
      }
      return u.parts().stream()
          .filter(p -> p.id().equals(partId))
          .map(UseSummary.Part::name)
          .findFirst()
          .orElse(null);
    }
  }
}
