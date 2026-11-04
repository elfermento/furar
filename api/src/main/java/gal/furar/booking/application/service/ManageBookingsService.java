// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.in.BookingCalendar;
import gal.furar.booking.application.port.in.BookingSearch;
import gal.furar.booking.application.port.in.BookingSort;
import gal.furar.booking.application.port.in.BookingsOverview;
import gal.furar.booking.application.port.in.CalendarView;
import gal.furar.booking.application.port.in.ManageBookingsUseCase;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.out.BookingFilter;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.StartCount;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.facility.FacilityQueries;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.RequiresPermission;
import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As reservas do concello para o persoal (plan do dominio 2, §2.2; maquetas 26 e 27). Os días son
 * de Europe/Madrid; os nomes saen nunha lectura por lista (sen N+1) e o calendario do mes e da
 * semana de todas as instalacións só conta, nunha consulta.
 */
@Service
public class ManageBookingsService implements ManageBookingsUseCase {

  static final ZoneId ZONE = BookingRules.ZONE;

  /** «Cobros»: de hoxe aos próximos 30 días. */
  static final int COLLECTION_DAYS = 30;

  /** Un código enteiro ou só os catro caracteres despois de {@code FUR-}. */
  private static final Pattern CODE =
      Pattern.compile("^(?:FUR-)?([A-HJ-NP-Z2-9]{4})$", Pattern.CASE_INSENSITIVE);

  private static final Set<BookingStatus> CANCELLED =
      Set.of(BookingStatus.CANCELLED_BY_PERSON, BookingStatus.CANCELLED_BY_MUNICIPALITY);

  private final BookingRepository bookings;
  private final BookingViews views;
  private final FacilityQueries facilities;
  private final Clock clock;

  public ManageBookingsService(
      BookingRepository bookings, BookingViews views, FacilityQueries facilities, Clock clock) {
    this.bookings = bookings;
    this.views = views;
    this.facilities = facilities;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.VIEW)
  public PageResult<ManagedBooking> search(BookingSearch search) {
    if (search.from() != null && search.to() != null && search.from().isAfter(search.to())) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("name", "from"));
    }
    String text = search.q() == null ? "" : search.q().strip();
    BookingFilter filter =
        new BookingFilter(
            search.from() == null ? null : startOf(search.from()),
            search.to() == null ? null : startOf(search.to().plusDays(1)),
            search.facilityIds(),
            search.payments(),
            search.statuses(),
            text.isEmpty() ? null : folded(text),
            text.isEmpty() ? null : code(text),
            search.sort() == BookingSort.RECENT);
    PageResult<Booking> page = bookings.search(filter, search.page(), search.size());
    return new PageResult<>(
        views.managed(page.items(), clock.instant()), page.page(), page.size(), page.totalItems());
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.VIEW)
  public BookingsOverview overview(int size) {
    LocalDate today = LocalDate.now(clock.withZone(ZONE));
    Instant since = startOf(today);
    Instant until = startOf(today.plusDays(COLLECTION_DAYS));
    PageResult<Booking> latest =
        bookings.search(filter(null, null, Set.of(), Set.of(), true), 0, size);
    PageResult<Booking> pending =
        bookings.search(
            filter(
                since,
                until,
                Set.of(PaymentStatus.PENDING),
                Set.of(BookingStatus.CONFIRMED),
                false),
            0,
            size);
    PageResult<Booking> toRefund =
        bookings.search(
            filter(null, null, Set.of(PaymentStatus.TO_REFUND), Set.of(), false), 0, size);
    PageResult<Booking> cancelled =
        bookings.search(filter(since, until, Set.of(), CANCELLED, false), 0, size);
    // Unha soa lectura de nomes para as catro listas.
    List<Booking> all = new ArrayList<>();
    List.of(latest, pending, toRefund, cancelled).forEach(p -> all.addAll(p.items()));
    List<ManagedBooking> named = views.managed(all, clock.instant());
    int a = latest.items().size();
    int b = a + pending.items().size();
    int c = b + toRefund.items().size();
    return new BookingsOverview(
        named.subList(0, a),
        new BookingsOverview.Tab(pending.totalItems(), named.subList(a, b)),
        new BookingsOverview.Tab(toRefund.totalItems(), named.subList(b, c)),
        new BookingsOverview.Tab(cancelled.totalItems(), named.subList(c, named.size())));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.VIEW)
  public ManagedBooking get(BookingId id) {
    return views.managed(
        bookings.findById(id).orElseThrow(() -> new DomainException(BookingError.NOT_FOUND)),
        clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.VIEW)
  public BookingCalendar calendar(CalendarView view, LocalDate date, @Nullable UUID facilityId) {
    LocalDate from =
        switch (view) {
          case DAY -> date;
          case WEEK -> date.with(DayOfWeek.MONDAY);
          case MONTH -> date.withDayOfMonth(1);
        };
    LocalDate to =
        switch (view) {
          case DAY -> date;
          case WEEK -> from.plusDays(6);
          case MONTH -> date.withDayOfMonth(date.lengthOfMonth());
        };
    Instant since = startOf(from);
    Instant until = startOf(to.plusDays(1));
    if (view == CalendarView.MONTH) {
      return new BookingCalendar(
          view,
          from,
          to,
          List.of(),
          counts(bookings.countConfirmedStarting(since, until, facilityId, false)),
          List.of(),
          List.of());
    }
    List<BookingCalendar.Opening> openings = openings(facilityId, from, to);
    if (view == CalendarView.WEEK && facilityId == null) {
      return new BookingCalendar(
          view,
          from,
          to,
          List.of(),
          List.of(),
          counts(bookings.countConfirmedStarting(since, until, null, true)),
          openings);
    }
    return new BookingCalendar(
        view,
        from,
        to,
        views.managed(bookings.confirmedStarting(since, until, facilityId), clock.instant()),
        List.of(),
        List.of(),
        openings);
  }

  /** Os tramos abertos do período (a instalación ou todas), cun número fixo de lecturas. */
  private List<BookingCalendar.Opening> openings(
      @Nullable UUID facilityId, LocalDate from, LocalDate to) {
    List<BookingCalendar.Opening> openings = new ArrayList<>();
    facilities
        .openingIntervalsByFacility(facilityId, from, to)
        .forEach(
            (id, intervals) ->
                intervals.forEach(
                    i ->
                        openings.add(
                            new BookingCalendar.Opening(
                                id, i.date(), i.startMinute(), i.endMinute()))));
    return openings;
  }

  private static List<BookingCalendar.Count> counts(List<StartCount> counts) {
    return counts.stream()
        .map(c -> new BookingCalendar.Count(c.date(), c.hour(), c.count()))
        .toList();
  }

  private static BookingFilter filter(
      @Nullable Instant since,
      @Nullable Instant until,
      Set<PaymentStatus> payments,
      Set<BookingStatus> statuses,
      boolean recentFirst) {
    return new BookingFilter(since, until, Set.of(), payments, statuses, null, null, recentFirst);
  }

  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(ZONE).toInstant();
  }

  /** En minúsculas e sen acentos, como o compara a base. */
  static String folded(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  /** O código se o texto pode selo ({@code FUR-7K2Q} ou {@code 7k2q}); se non, nada. */
  static @Nullable String code(String text) {
    Matcher m = CODE.matcher(text);
    return m.matches() ? "FUR-" + m.group(1).toUpperCase(Locale.ROOT) : null;
  }
}
