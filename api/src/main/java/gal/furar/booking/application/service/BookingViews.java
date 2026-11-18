// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.ActivitySessionLinks;
import gal.furar.booking.BookingMailLog;
import gal.furar.booking.BookingPermission;
import gal.furar.booking.NoActivitySessionLinks;
import gal.furar.booking.NoBookingMailLog;
import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.BookingActivity;
import gal.furar.booking.application.port.in.BookingOrganization;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.OpenMatchSummary;
import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.BookingReviews;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.QrEncoder;
import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.Attendance;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.ReviewRule;
import gal.furar.booking.domain.season.Series;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
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
  private final BookingRepository bookings;
  private final QrEncoder qr;
  private final BookingCustomFields customFields;
  private final StaffAccounts staff;
  private final ActivitySessionLinks activities;
  private final BookingMailLog mails;
  private final NoShowPenalty penalty;
  private final BookingReviews reviews;
  private final OpenMatches openMatches;
  private final SeriesStore series;
  private final Organizations organizations;
  private final CurrentActor currentActor;

  BookingViews(
      FacilityQueries facilities,
      BookingRepository bookings,
      QrEncoder qr,
      BookingCustomFields customFields,
      ObjectProvider<StaffAccounts> staff,
      ObjectProvider<ActivitySessionLinks> activities,
      ObjectProvider<BookingMailLog> mails,
      NoShowPenalty penalty,
      BookingReviews reviews,
      OpenMatches openMatches,
      SeriesStore series,
      Organizations organizations,
      CurrentActor currentActor) {
    this.series = series;
    this.organizations = organizations;
    this.currentActor = currentActor;
    this.penalty = penalty;
    this.openMatches = openMatches;
    this.reviews = reviews;
    this.facilities = facilities;
    this.bookings = bookings;
    this.qr = qr;
    this.customFields = customFields;
    // Un só bean de cada SPI (ADR-0005): sen ningún, as implementacións baleiras.
    this.staff = staff.getIfAvailable(NoStaffAccounts::new);
    // Un só ActivitySessionLinks (ADR-0041): sen ningún, NoActivitySessionLinks.
    this.activities = activities.getIfAvailable(NoActivitySessionLinks::new);
    this.mails = mails.getIfAvailable(NoBookingMailLog::new);
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
    Map<UUID, List<Participant>> people = people(bookings);
    // As valoracións (ADR-0037): a nota das xa valoradas e, para as demais, se se poden valorar.
    Map<UUID, Integer> stars = withCover ? reviews.starsOf(ids(bookings)) : Map.of();
    Map<UUID, Instant> controlled =
        withCover && bookings.stream().anyMatch(b -> b.isDone(now))
            ? facilities.entryControl().stream()
                .collect(
                    Collectors.toMap(
                        FacilityQueries.EntryControl::facilityId,
                        FacilityQueries.EntryControl::since))
            : Map.of();
    // As partidas abertas das reservas (ADR-0040), nunha lectura.
    Map<UUID, OpenMatches.Listed> matches =
        withCover ? openMatches.ofBookings(ids(bookings)) : Map.of();
    // As da entidade (ADR-0039): o seu nome e, nas sesións, a tempada.
    Map<UUID, BookingOrganization> owners = owners(bookings, true);
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
                    // Os dunha reserva do mostrador escribiunos o persoal: a conta ligada ve o
                    // mesmo que leva o correo de confirmación e nada máis (ADR-0031, punto 7).
                    b.bookedBy() != null
                        ? CustomFieldValues.empty()
                        : fields.getOrDefault(b.id().value(), CustomFieldValues.empty()),
                    people.getOrDefault(b.id().value(), List.of()),
                    withCover
                        && !stars.containsKey(b.id().value())
                        && ReviewRule.reviewable(b, now, controlled.get(b.slot().facilityId())),
                    stars.get(b.id().value()),
                    summary(matches.get(b.id().value())),
                    owners.get(b.id().value())))
        .toList();
  }

  /** O detalle dunha reserva: tamén o último correo que saíu e a súa partida aberta. */
  ManagedBooking managed(Booking booking, Instant now) {
    ManagedBooking view =
        managed(List.of(booking), now, true)
            .getFirst()
            .withOpenMatch(
                summary(openMatches.ofBookings(ids(List.of(booking))).get(booking.id().value())))
            // A tempada da sesión, só para quen xestiona as entidades (ADR-0039, punto 11).
            .withOrganization(owners(List.of(booking), canSeeSeasons()).get(booking.id().value()));
    // Cunha falta e a penalización acesa, as faltas que leva a persoa (ADR-0032, punto 7).
    Holder holder = booking.holder();
    String emailKey = holder == null ? null : holder.emailKey();
    if (booking.attendance() != Attendance.NO_SHOW || emailKey == null) {
      return view;
    }
    return view.withNoShow(penalty.standing(emailKey, now).orElse(null));
  }

  List<ManagedBooking> managed(List<Booking> bookings, Instant now) {
    return managed(bookings, now, false);
  }

  private List<ManagedBooking> managed(List<Booking> bookings, Instant now, boolean withMail) {
    if (bookings.isEmpty()) {
      return List.of();
    }
    Names names = names(bookings);
    Map<UUID, CustomFieldValues> fields = customFields.forStaff(ids(bookings));
    Map<UUID, List<Participant>> people = people(bookings);
    // Quen fixo a reserva: só o persoal; a reserva extra dunha entidade mostra a entidade.
    Map<String, StaffAccounts.Member> members =
        staff.members(
            bookings.stream()
                .filter(b -> b.organizationId() == null || b.isSession())
                .map(Booking::bookedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    Map<UUID, BookingOrganization> owners = owners(bookings, false);
    List<UUID> activitySessions =
        bookings.stream().filter(Booking::isActivitySession).map(b -> b.id().value()).toList();
    Map<UUID, UUID> sessions =
        activitySessions.isEmpty() ? Map.of() : activities.sessionsByBooking(activitySessions);
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
                        fields.getOrDefault(b.id().value(), CustomFieldValues.empty()),
                        b.bookedBy() == null ? null : members.get(b.bookedBy()),
                        withMail ? mails.lastSent(b.id().value()).orElse(null) : null,
                        people.getOrDefault(b.id().value(), List.of()))
                    .withOrganization(owners.get(b.id().value()))
                    .withActivity(
                        b.activityId() == null
                            ? null
                            : new BookingActivity(b.activityId(), sessions.get(b.id().value()))))
        .toList();
  }

  /** Se quen mira xestiona as entidades e as tempadas. */
  private boolean canSeeSeasons() {
    String slug = TenantContext.require().slug();
    return currentActor
        .current()
        .map(a -> a.can(BookingPermission.ORGANIZATIONS, slug))
        .orElse(false);
  }

  /**
   * A entidade de cada reserva da entidade (o nome é o do titular, unha copia; nas anonimizadas, o
   * de agora) e, con {@code withSeason}, a tempada de cada sesión: unha lectura por tempada.
   */
  private Map<UUID, BookingOrganization> owners(List<Booking> list, boolean withSeason) {
    List<Booking> owned = list.stream().filter(b -> b.organizationId() != null).toList();
    if (owned.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names =
        organizations.names(
            owned.stream()
                .filter(Booking::isAnonymised)
                .map(Booking::organizationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    Map<UUID, BookingOrganization.Season> seasons =
        withSeason
            ? seasons(
                owned.stream().map(Booking::seriesId).filter(Objects::nonNull).distinct().toList())
            : Map.of();
    Map<UUID, BookingOrganization> result = new HashMap<>();
    for (Booking b : owned) {
      UUID org = Objects.requireNonNull(b.organizationId());
      Holder holder = b.holder();
      String name = holder != null ? holder.name() : names.getOrDefault(org, "");
      result.put(
          b.id().value(),
          new BookingOrganization(
              org, name, b.seriesId(), b.seriesId() == null ? null : seasons.get(b.seriesId())));
    }
    return result;
  }

  private Map<UUID, BookingOrganization.Season> seasons(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, SeriesStore.Counts> counts = series.counts(ids);
    Map<UUID, BookingOrganization.Season> result = new HashMap<>();
    for (UUID id : ids) {
      Optional<Series> found = series.find(id);
      found.ifPresent(
          s ->
              result.put(
                  id,
                  new BookingOrganization.Season(
                      id,
                      s.schedule().isoWeekdays(),
                      s.schedule().startMinute(),
                      s.schedule().endMinute(),
                      s.schedule().firstDate(),
                      s.schedule().lastDate(),
                      counts.getOrDefault(id, new SeriesStore.Counts(0, 0)).confirmed(),
                      s.paymentStatus().name())));
    }
    return result;
  }

  private static @Nullable OpenMatchSummary summary(OpenMatches.@Nullable Listed listed) {
    return listed == null ? null : OpenMatchViews.summary(listed);
  }

  /** As persoas das reservas por prazas da lista, nunha lectura. */
  private Map<UUID, List<Participant>> people(List<Booking> list) {
    List<BookingId> bySeats =
        list.stream()
            .filter(b -> b.slot().rentalMode() == RentalMode.SEATS)
            .map(Booking::id)
            .toList();
    return bySeats.isEmpty() ? Map.of() : bookings.participants(bySeats);
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
