// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingRules;
import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O {@link BookingImpact} real (ADR-0025, punto 16; plan do dominio 2, §1.2.17): que reservas
 * confirmadas e aínda sen rematar deixa fóra cada {@link FacilityChange} e que facer con elas.
 * {@code facility} chámao baixo o bloqueo da instalación e na súa transacción, así que aquí non se
 * toma outro bloqueo nin se comproban permisos: xa os comprobou o caso de uso de {@code facility}.
 *
 * <ul>
 *   <li>{@code Closure} e {@code Withdrawal}: todas as da instalación.
 *   <li>{@code Block}: as que pisan o período.
 *   <li>{@code ScheduleChanged}: as que pisan algún dos tramos que se perden.
 *   <li>{@code ExclusiveUse}: as doutros usos nos días e días da semana que pasa a bloquear.
 *   <li>{@code UseChanged} e {@code UseArchived}: as do uso.
 *   <li>{@code SeatsChanged}: ningunha (baixar con reservas xa o rexeita {@code UseChanged}).
 * </ul>
 */
@Service
class BookingImpactService implements BookingImpact {

  static final String CANCEL = "booking.cancel";
  static final ZoneId ZONE = BookingRules.ZONE;

  private final BookingRepository bookings;
  private final FacilityQueries facilities;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  BookingImpactService(
      BookingRepository bookings,
      FacilityQueries facilities,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.bookings = bookings;
    this.facilities = facilities;
    this.currentActor = currentActor;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AffectedBooking> affectedBy(FacilityChange change) {
    List<Booking> found = affected(change, clock.instant());
    if (found.isEmpty()) {
      return List.of();
    }
    refuseUnlessCanDecide(found.size());
    Map<UUID, String> useNames =
        facilities
            .usesById(found.stream().map(b -> b.slot().useId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(UseSummary::id, UseSummary::name));
    return found.stream().map(b -> view(b, useNames.getOrDefault(b.slot().useId(), ""))).toList();
  }

  /**
   * Cancela ou mantén cada reserva decidida. Só as que este cambio afecta agora: {@code facility}
   * xa comprobou que as decisións son exactamente para elas. {@code SeatsChanged} non trae
   * decisións: leva as prazas novas aos contadores das franxas que aínda non empezaron (ADR-0012).
   */
  @Override
  @Transactional
  public void apply(FacilityChange change, List<BookingDecision> decisions) {
    if (change instanceof FacilityChange.SeatsChanged seats) {
      bookings.resizeSeats(seats.useId(), seats.capacity(), clock.instant());
      return;
    }
    if (decisions.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    Map<UUID, Booking> affected =
        affected(change, now).stream()
            .collect(Collectors.toMap(b -> b.id().value(), Function.identity()));
    String actor = subject();
    TenantRef tenant = TenantContext.require();
    for (BookingDecision decision : decisions) {
      Booking before = affected.get(decision.bookingId());
      if (before == null) {
        throw new IllegalArgumentException("Not affected by the change: " + decision.bookingId());
      }
      switch (decision.action()) {
        case KEEP -> bookings.keep(before, now, actor);
        case CANCEL -> cancel(change, before, channels(decision.channels()), actor, now, tenant);
      }
    }
  }

  /**
   * Só quen pode cancelar reservas ve as afectadas e decide sobre elas: a plataforma configura
   * instalacións, pero os datos da veciñanza quedan fóra (ADR-0028, revisión do 2026-11-03). Só o
   * número, sen datos das reservas.
   */
  private void refuseUnlessCanDecide(int count) {
    String slug = TenantContext.require().slug();
    boolean allowed =
        currentActor.current().map(a -> a.can(BookingPermission.CANCEL, slug)).orElse(false);
    if (!allowed) {
      throw new DomainException(BookingError.AFFECTED_NEEDS_MANAGER, Map.of("count", count));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasBookings(UUID facilityId) {
    return bookings.anyOf(facilityId);
  }

  private void cancel(
      FacilityChange change,
      Booking before,
      Set<BookingCancelled.Channel> channels,
      String actor,
      Instant now,
      TenantRef tenant) {
    Booking after = before.cancelByMunicipality(now, actor);
    bookings.cancel(before, after, actor, channels);
    events.publishEvent(
        new BookingCancelled(
            tenant.id(), before.id().value(), BookingCancelled.By.MUNICIPALITY, channels));
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", before.id().value().toString());
    details.put("channels", channels.stream().map(Enum::name).sorted().toList());
    details.put("payment", after.paymentStatus().name());
    details.put("facilityChange", change.getClass().getSimpleName());
    audit.record(new AuditEvent(CANCEL, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
  }

  /** As reservas confirmadas e sen rematar que o cambio deixa fóra, da primeira á última. */
  private List<Booking> affected(FacilityChange change, Instant now) {
    UUID facility = change.facilityId();
    return switch (change) {
      case FacilityChange.Closure c -> bookings.confirmedNotEnded(facility, null, now, null, null);
      case FacilityChange.Withdrawal w ->
          bookings.confirmedNotEnded(facility, null, now, null, null);
      case FacilityChange.Block b ->
          bookings.confirmedNotEnded(facility, null, now, b.start(), b.end());
      case FacilityChange.UseChanged u ->
          bookings.confirmedNotEnded(facility, u.useId(), now, null, null);
      case FacilityChange.UseArchived u ->
          bookings.confirmedNotEnded(facility, u.useId(), now, null, null);
      case FacilityChange.SeatsChanged s -> List.of();
      case FacilityChange.ScheduleChanged s -> lostHours(facility, s.lost(), now);
      case FacilityChange.ExclusiveUse e -> otherUses(facility, e, now);
    };
  }

  private List<Booking> lostHours(UUID facility, List<OpeningInterval> lost, Instant now) {
    if (lost.isEmpty()) {
      return List.of();
    }
    Instant from = lost.stream().map(OpeningInterval::start).min(Instant::compareTo).orElseThrow();
    Instant to = lost.stream().map(OpeningInterval::end).max(Instant::compareTo).orElseThrow();
    return bookings.confirmedNotEnded(facility, null, now, from, to).stream()
        .filter(
            b ->
                lost.stream()
                    .anyMatch(
                        i ->
                            b.slot().start().isBefore(i.end())
                                && i.start().isBefore(b.slot().end())))
        .toList();
  }

  private List<Booking> otherUses(UUID facility, FacilityChange.ExclusiveUse change, Instant now) {
    if (change.dates().isEmpty()) {
      return List.of();
    }
    LocalDate first =
        change.dates().stream()
            .map(FacilityChange.Dates::from)
            .min(LocalDate::compareTo)
            .orElseThrow();
    LocalDate last =
        change.dates().stream()
            .map(FacilityChange.Dates::to)
            .max(LocalDate::compareTo)
            .orElseThrow();
    Predicate<LocalDate> blocked =
        date ->
            (change.weekdays() & (1 << (date.getDayOfWeek().getValue() - 1))) != 0
                && change.dates().stream()
                    .anyMatch(d -> !date.isBefore(d.from()) && !date.isAfter(d.to()));
    return bookings
        .confirmedNotEnded(
            facility,
            null,
            now,
            first.atStartOfDay(ZONE).toInstant(),
            last.plusDays(1).atStartOfDay(ZONE).toInstant())
        .stream()
        .filter(b -> !b.slot().useId().equals(change.useId()))
        .filter(b -> blocked.test(LocalDate.ofInstant(b.slot().start(), ZONE)))
        .toList();
  }

  /** A reserva para o modal, coas horas de reloxo de parede en Europe/Madrid. */
  private static AffectedBooking view(Booking booking, String useName) {
    ZonedDateTime start = booking.slot().start().atZone(ZONE);
    ZonedDateTime end = booking.slot().end().atZone(ZONE);
    int endMinute =
        end.toLocalDate().isAfter(start.toLocalDate())
            ? 1440
            : end.getHour() * 60 + end.getMinute();
    return new AffectedBooking(
        booking.id().value(),
        booking.requireHolder().name(),
        true,
        start.toLocalDate(),
        start.getHour() * 60 + start.getMinute(),
        endMinute,
        useName);
  }

  private static Set<BookingCancelled.Channel> channels(Set<BookingDecision.Channel> channels) {
    Set<BookingCancelled.Channel> mapped = EnumSet.noneOf(BookingCancelled.Channel.class);
    channels.forEach(c -> mapped.add(BookingCancelled.Channel.valueOf(c.name())));
    return mapped;
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }
}
