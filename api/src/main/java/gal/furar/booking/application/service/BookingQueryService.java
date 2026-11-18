// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.BookingQueries;
import gal.furar.booking.BookingReminder;
import gal.furar.booking.BookingSummary;
import gal.furar.booking.CancelledWaitsNotice;
import gal.furar.booking.OpenMatchNotice;
import gal.furar.booking.SeriesNotice;
import gal.furar.booking.SeriesRequestNotice;
import gal.furar.booking.WaitNotice;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.out.BookingReminders;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.BookingWaits;
import gal.furar.booking.application.port.out.CalendarFile;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.QrEncoder;
import gal.furar.booking.application.port.out.ResidentBookings;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.CancelledWait;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.ParticipantKind;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link BookingQueries}) para outros módulos. Sempre no
 * concello actual (o repositorio falla sen el); sen permisos propios porque non é un caso de uso.
 */
@Service
class BookingQueryService implements BookingQueries {

  /** Píxeles por módulo do QR do correo: ~200 px, nítido a 150 px nunha pantalla densa. */
  private static final int QR_MODULE_PIXELS = 8;

  private static final int QR_QUIET_MODULES = 2;

  private final BookingRepository repository;
  private final BookingViews views;
  private final CalendarFile calendar;
  private final QrEncoder qr;
  private final ResidentBookings residents;
  private final WaitNotices waitNotices;
  private final BookingWaits waits;
  private final FacilityQueries facilities;
  private final BookingReminders reminders;
  private final ReminderLinks links;
  private final OpenMatchNotices openMatchNotices;
  private final Organizations organizations;
  private final SeriesNotices seriesNotices;
  private final Clock clock;

  BookingQueryService(
      BookingRepository repository,
      BookingViews views,
      CalendarFile calendar,
      QrEncoder qr,
      ResidentBookings residents,
      WaitNotices waitNotices,
      BookingWaits waits,
      FacilityQueries facilities,
      BookingReminders reminders,
      ReminderLinks links,
      OpenMatchNotices openMatchNotices,
      Organizations organizations,
      SeriesNotices seriesNotices,
      Clock clock) {
    this.organizations = organizations;
    this.seriesNotices = seriesNotices;
    this.repository = repository;
    this.views = views;
    this.calendar = calendar;
    this.qr = qr;
    this.residents = residents;
    this.waitNotices = waitNotices;
    this.waits = waits;
    this.facilities = facilities;
    this.reminders = reminders;
    this.links = links;
    this.openMatchNotices = openMatchNotices;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BookingSummary> find(UUID id) {
    return repository.findById(new BookingId(id)).map(BookingQueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OpenMatchNotice> openMatchNotice(
      UUID matchId, OpenMatchNotice.Kind kind, @Nullable UUID playerId) {
    return openMatchNotices.notice(matchId, kind, playerId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BookingNotice> notice(UUID id) {
    // Unha anonimizada xa non ten a quen avisar; unha do persoal sen correo, tampouco. A dunha
    // entidade vai ao correo da entidade, mentres está dada de alta (ADR-0039, punto 12).
    return repository
        .findById(new BookingId(id))
        .filter(b -> !b.isAnonymised())
        .flatMap(
            b -> {
              if (b.organizationId() == null) {
                return b.requireHolder().email() == null
                    ? Optional.empty()
                    : Optional.of(notice(b));
              }
              return organizations
                  .find(b.organizationId())
                  .filter(o -> o.email() != null)
                  .map(o -> notice(b, o.name(), Objects.requireNonNull(o.email())));
            });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BookingReminder> reminder(UUID id) {
    Instant now = clock.instant();
    // Co estado de agora: unha cancelada, unha sen correo ou cun prazo xa pasado, non; un correo
    // que deu de baixa os recordatorios mentres tanto, tampouco.
    return repository
        .findById(new BookingId(id))
        .filter(b -> !b.isAnonymised() && b.status() == BookingStatus.CONFIRMED)
        .filter(b -> b.requireHolder().email() != null && now.isBefore(b.cancelUntil()))
        .filter(
            b -> {
              String key = b.requireHolder().emailKey();
              return key != null && !reminders.optedOut(key);
            })
        .map(
            b ->
                new BookingReminder(notice(b), links.issue(TenantContext.require().id(), id, now)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<WaitNotice> waitNotices(UUID useId, Instant start, Instant releasedAt) {
    return waitNotices.of(useId, start, releasedAt);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CancelledWaitsNotice> cancelledWaitNotices(UUID cancellationId) {
    List<CancelledWait> cancelled = waits.cancelled(cancellationId);
    if (cancelled.isEmpty()) {
      return List.of();
    }
    // Todas son da mesma instalación: o nome de agora (tamén se está de baixa).
    UUID facilityId = cancelled.getFirst().facilityId();
    String facilityName = facilities.find(facilityId).map(FacilitySummary::name).orElse("");
    // Un correo por persoa: xa veñen por correo e inicio.
    Map<String, List<CancelledWait>> byEmail = new LinkedHashMap<>();
    for (CancelledWait w : cancelled) {
      byEmail.computeIfAbsent(w.emailKey(), k -> new ArrayList<>()).add(w);
    }
    return byEmail.values().stream()
        .map(
            list ->
                new CancelledWaitsNotice(
                    list.stream().map(CancelledWait::id).min(UUID::compareTo).orElseThrow(),
                    list.getFirst().emailKey(),
                    list.getFirst().language().code(),
                    facilityId,
                    facilityName,
                    list.stream()
                        .map(w -> new CancelledWaitsNotice.Slot(w.start(), w.end()))
                        .toList()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SeriesNotice> seriesNotice(UUID seriesId) {
    return seriesNotices.series(seriesId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SeriesRequestNotice> seriesRequestNotice(UUID requestId) {
    return seriesNotices.request(requestId);
  }

  @Override
  public boolean hasResidentBookings(String subject) {
    return residents.existAnywhere(subject);
  }

  private BookingNotice notice(Booking booking) {
    Holder holder = booking.requireHolder();
    return notice(booking, holder.name(), Objects.requireNonNull(holder.email(), "email"));
  }

  private BookingNotice notice(Booking booking, String name, String email) {
    MyBooking view = views.withoutCover(booking, clock.instant());
    Holder holder = booking.requireHolder();
    return new BookingNotice(
        booking.id().value(),
        booking.code().value(),
        Objects.requireNonNull(booking.version(), "version"),
        booking.slot().facilityId(),
        view.facilityName(),
        view.facilityAddress(),
        view.useName(),
        view.partName(),
        booking.slot().start(),
        booking.slot().end(),
        BookingSummary.Status.valueOf(booking.status().name()),
        BookingNotice.Payment.valueOf(booking.paymentStatus().name()),
        BookingNotice.PriceKind.valueOf(booking.price().kind().name()),
        booking.price().amountCents(),
        booking.cancelUntil(),
        name,
        email,
        holder.language().code(),
        holder.hasAccount(),
        qr.png(booking.code().value(), QR_MODULE_PIXELS, QR_QUIET_MODULES),
        calendar.of(
            booking, view.facilityName(), view.facilityAddress(), view.useName(), view.partName()),
        booking.seats(),
        view.participants().stream()
            .map(p -> p.kind() == ParticipantKind.HOLDER ? holder.name() : p.name())
            .filter(Objects::nonNull)
            .toList(),
        booking.bookedBy() != null && booking.organizationId() == null,
        booking.organizationId() != null,
        booking.isSession());
  }

  static BookingSummary summary(Booking booking) {
    return new BookingSummary(
        booking.id().value(),
        booking.code().value(),
        booking.slot().facilityId(),
        booking.slot().useId(),
        booking.slot().partId(),
        booking.slot().start(),
        booking.slot().end(),
        BookingSummary.Status.valueOf(booking.status().name()));
  }
}
