// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import gal.furar.booking.BookingStatistics;
import gal.furar.booking.BookingStatistics.CancellationBucket;
import gal.furar.booking.BookingStatistics.FacilityMoney;
import gal.furar.booking.BookingStatistics.PeopleBucket;
import gal.furar.booking.BookingStatistics.UseBucket;
import gal.furar.booking.BookingStatistics.UseDay;
import gal.furar.booking.BookingStatistics.UseWeekdayHour;
import gal.furar.booking.StatsQuery;
import gal.furar.booking.StatsUnit;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.indicators.IndicatorsPermission;
import gal.furar.indicators.application.port.in.IndicatorsPanel;
import gal.furar.indicators.application.port.in.IndicatorsPanel.BucketOccupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Cancellations;
import gal.furar.indicators.application.port.in.IndicatorsPanel.FacilityOccupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.FacilityRevenue;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpi;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpis;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.PeakHour;
import gal.furar.indicators.application.port.in.IndicatorsPanel.People;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Point;
import gal.furar.indicators.application.port.in.IndicatorsQuery;
import gal.furar.indicators.application.port.in.ReadIndicatorsUseCase;
import gal.furar.indicators.domain.Comparison;
import gal.furar.indicators.domain.IndicatorsError;
import gal.furar.indicators.domain.OpeningMinutes;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.indicators.domain.PeriodUnit;
import gal.furar.indicators.domain.Ratio;
import gal.furar.indicators.domain.ReportPeriod;
import gal.furar.indicators.domain.TimeSpan;
import gal.furar.indicators.domain.UseWeight;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O panel de indicadores (ADR-0026; plan do dominio 3, §1.2.4–§1.2.6 e §2.3). Compón dúas APIs
 * públicas, sempre no concello actual: os agregados de {@link BookingStatistics} e as instalacións,
 * os usos e as horas abertas de {@link FacilityQueries}. Nunca le táboas.
 *
 * <p>Lecturas fixas por petición, non dependen do número de instalacións nin de usos nin da
 * unidade: seis de reservas (sete na semana, no trimestre e no ano; nove nun rango libre), as
 * instalacións, os usos (dúas veces se se filtra por uso) e as horas abertas de toda a evolución
 * nunha lectura ({@link FacilityQueries#longOpeningIntervalsByFacility}, ata {@value
 * FacilityQueries#MAX_LONG_OPENING_DAYS} días: a evolución anual pide cinco anos). Non se len dúas
 * veces as mesmas cifras: as persoas do período son o último punto da súa evolución (agás no rango
 * libre) e as dos últimos meses saen da evolución cando esta é mensual. No período en curso, catro
 * máis: as cifras e as persoas do tramo «ata hoxe» e do mesmo tramo do anterior ({@link
 * ReportPeriod#comparison}).
 *
 * <p>A ocupación cóntase por instalación: os minutos reservados (co peso do uso) non pasan dos
 * abertos desa instalación no tramo, e as que non abren no tramo non contan. As de baixa nunca
 * abren. Co filtro de uso, unha instalación só abre os días que vale algún dos usos escollidos
 * (aínda que o uso xa estea arquivado: interesa o seu histórico).
 *
 * <p>Tempo máximo: {@value #TIMEOUT_SECONDS} s para toda a transacción. Spring pasa o que queda a
 * cada sentenza ({@code Statement#setQueryTimeout}, tamén no SQL de {@code booking}) e PostgreSQL
 * cancela a que se pase; a petición remata cun erro en vez de reter unha conexión.
 */
@Service
class IndicatorsService implements ReadIndicatorsUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(IndicatorsService.class);

  /** Segundos como moito para todo o panel. */
  static final int TIMEOUT_SECONDS = 10;

  /** O primeiro día que se pode pedir. */
  static final LocalDate FIRST_DAY = LocalDate.of(2000, 1, 1);

  /** Anos cara adiante que se poden pedir (reservas xa feitas). */
  static final int YEARS_AHEAD = 2;

  /** Usos como moito nun filtro. */
  static final int MAX_USES = 50;

  private static final int PEOPLE_MONTHS = 6;
  private static final int PEOPLE_MONTHS_IN_A_YEAR = 12;
  private static final int HOURS = 24;

  private final BookingStatistics statistics;
  private final FacilityQueries facilities;
  private final CurrentActor actor;
  private final Clock clock;

  IndicatorsService(
      BookingStatistics statistics, FacilityQueries facilities, CurrentActor actor, Clock clock) {
    this.statistics = statistics;
    this.facilities = facilities;
    this.actor = actor;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
  @RequiresPermission(IndicatorsPermission.Code.VIEW)
  public IndicatorsPanel read(IndicatorsQuery query) {
    LocalDate today = LocalDate.now(clock.withZone(ReportPeriod.ZONE));
    ReportPeriod period = period(query, today);
    if (query.useIds().size() > MAX_USES
        || new HashSet<>(query.useIds()).size() != query.useIds().size()) {
      throw filterInvalid();
    }
    Map<UUID, FacilitySummary> facilityById = new LinkedHashMap<>();
    facilities.all().forEach(f -> facilityById.put(f.id(), f));
    UUID facilityId = query.facilityId();
    if (facilityId != null && !facilityById.containsKey(facilityId)) {
      throw filterInvalid();
    }
    Map<UUID, UseSummary> uses = new HashMap<>();
    if (!query.useIds().isEmpty()) {
      facilities.usesById(query.useIds()).forEach(u -> uses.put(u.id(), u));
      if (uses.size() != query.useIds().size()
          || (facilityId != null
              && uses.values().stream().anyMatch(u -> !u.facilityId().equals(facilityId)))) {
        throw filterInvalid();
      }
    }
    Filter filter = new Filter(facilityId, Set.copyOf(query.useIds()), Map.copyOf(uses));
    trace(period, filter);
    return new Panel(period, filter, facilityById, uses, today).build();
  }

  /** O período pedido, dentro de {@code 2000-01-01 … hoxe + 2 anos}. */
  static ReportPeriod period(IndicatorsQuery query, LocalDate today) {
    LocalDate last = today.plusYears(YEARS_AHEAD);
    Predicate<@Nullable LocalDate> outside =
        d -> d != null && (d.isBefore(FIRST_DAY) || d.isAfter(last));
    if (query.kind() == PeriodKind.CUSTOM) {
      if (outside.test(query.from()) || outside.test(query.to())) {
        throw periodInvalid();
      }
      return ReportPeriod.custom(query.from(), query.to());
    }
    LocalDate anchor = query.date() == null ? today : query.date();
    if (outside.test(anchor)) {
      throw periodInvalid();
    }
    return ReportPeriod.of(query.kind(), anchor);
  }

  /**
   * Quen mira que, só en DEBUG (ADR-0026: sen {@code AuditLog}): o {@code sub} do xestor e os
   * filtros, sen datos da veciñanza. Non vai ao rexistro de cada día.
   */
  private void trace(ReportPeriod period, Filter filter) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "indicators read: subject={} municipality={} period={} facility={} uses={}",
          actor.current().map(Actor::subject).orElse("-"),
          TenantContext.require().id(),
          period,
          filter.facilityId() == null ? "all" : filter.facilityId(),
          filter.useIds().isEmpty() ? "all" : filter.useIds().size());
    }
  }

  private static DomainException periodInvalid() {
    return new DomainException(
        IndicatorsError.PERIOD_INVALID, Map.of("max", ReportPeriod.MAX_CUSTOM_DAYS));
  }

  /** Sen o id no texto: o mesmo erro para o que non existe e para o doutro concello. */
  private static DomainException filterInvalid() {
    return new DomainException(IndicatorsError.FILTER_INVALID);
  }

  static StatsUnit unit(PeriodUnit unit) {
    return StatsUnit.valueOf(unit.name());
  }

  /** Se o uso vale ese día polas súas datas e días da semana (arquivado ou non). */
  static boolean usedOn(UseSummary use, LocalDate date) {
    if ((use.weekdays() & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) {
      return false;
    }
    return use.periods().isEmpty()
        || use.periods().stream().anyMatch(p -> !date.isBefore(p.from()) && !date.isAfter(p.to()));
  }

  /**
   * @param useIds os usos escollidos; baleiro, todos
   * @param selected os mesmos usos, xa lidos
   */
  record Filter(@Nullable UUID facilityId, Set<UUID> useIds, Map<UUID, UseSummary> selected) {

    StatsQuery query(LocalDate from, LocalDate to) {
      return new StatsQuery(from, to, facilityId, useIds);
    }

    StatsQuery query(ReportPeriod period) {
      return query(period.from(), period.to());
    }
  }

  /** O cálculo dunha petición. */
  private final class Panel {

    private final ReportPeriod period;
    private final Filter filter;
    private final Map<UUID, FacilitySummary> facilityById;
    private final Map<UUID, UseSummary> uses;
    private final List<ReportPeriod> evolution;
    private final ReportPeriod previous;
    private final LocalDate today;

    /** Minutos abertos por instalación e día, de toda a evolución e do anterior. */
    private final Map<UUID, NavigableMap<LocalDate, Long>> open = new LinkedHashMap<>();

    /** Os minutos abertos do período, por día da semana e hora. */
    private OpeningMinutes openInPeriod = OpeningMinutes.of(List.of());

    Panel(
        ReportPeriod period,
        Filter filter,
        Map<UUID, FacilitySummary> facilityById,
        Map<UUID, UseSummary> selected,
        LocalDate today) {
      this.period = period;
      this.today = today;
      this.filter = filter;
      this.facilityById = facilityById;
      this.uses = new HashMap<>(selected);
      this.evolution = period.evolution();
      this.previous = period.previous();
    }

    IndicatorsPanel build() {
      boolean custom = period.kind() == PeriodKind.CUSTOM;
      PeriodUnit evolutionUnit = period.evolutionUnit();
      PeriodUnit breakdown = period.breakdownUnit();
      StatsQuery current = filter.query(period);
      StatsQuery evolutionQuery =
          filter.query(evolution.getFirst().from(), evolution.getLast().to());
      int months = period.kind() == PeriodKind.YEAR ? PEOPLE_MONTHS_IN_A_YEAR : PEOPLE_MONTHS;
      LocalDate lastMonth = PeriodUnit.MONTH.start(period.to());
      LocalDate firstMonth = PeriodUnit.MONTH.plus(lastMonth, -(months - 1L));

      List<UseDay> days = statistics.perUseAndDay(current);
      List<UseWeekdayHour> hours = statistics.perUseWeekdayHour(current);
      List<UseBucket> evolutionBuckets = statistics.perBucket(evolutionQuery, unit(evolutionUnit));
      List<PeopleBucket> evolutionPeople =
          statistics.peoplePerBucket(evolutionQuery, unit(evolutionUnit));
      Map<LocalDate, Long> peopleByStart = peopleByStart(evolutionPeople);
      // Fóra do rango libre, o período é o último punto da evolución: xa está contado.
      long people =
          custom
              ? statistics.people(current)
              : peopleByStart.getOrDefault(evolution.getLast().from(), 0L);
      List<CancellationBucket> cancellations = statistics.cancellations(current, unit(breakdown));
      List<FacilityMoney> money = statistics.money(current);
      // Cunha evolución mensual que os cubre (mes e rango libre), os últimos meses xa están lidos.
      boolean monthlyInEvolution =
          evolutionUnit == PeriodUnit.MONTH
              && !firstMonth.isBefore(evolution.getFirst().from())
              && lastMonth.equals(evolution.getLast().from());
      List<PeopleBucket> monthlyPeople =
          monthlyInEvolution
              ? evolutionPeople
              : statistics.peoplePerBucket(
                  filter.query(firstMonth, PeriodUnit.MONTH.end(lastMonth)), StatsUnit.MONTH);
      // Nun rango libre o anterior non é un punto da evolución: o ano só agrupa, súmase todo.
      List<UseBucket> previousBuckets =
          custom ? statistics.perBucket(filter.query(previous), StatsUnit.YEAR) : List.of();
      long previousPeople = custom ? statistics.people(filter.query(previous)) : 0;
      // No período en curso, a variación compara ata hoxe co mesmo tramo do anterior.
      Comparison comparison = period.comparison(today);
      StatsQuery nowQuery = filter.query(comparison.from(), comparison.to());
      StatsQuery beforeQuery = filter.query(comparison.previousFrom(), comparison.previousTo());
      List<UseBucket> nowToDate =
          comparison.toDate() ? statistics.perBucket(nowQuery, StatsUnit.YEAR) : List.of();
      List<UseBucket> beforeToDate =
          comparison.toDate() ? statistics.perBucket(beforeQuery, StatsUnit.YEAR) : List.of();
      long peopleNowToDate = comparison.toDate() ? statistics.people(nowQuery) : 0;
      long peopleBeforeToDate = comparison.toDate() ? statistics.people(beforeQuery) : 0;

      Set<UUID> seen = new HashSet<>();
      days.forEach(d -> seen.add(d.useId()));
      hours.forEach(h -> seen.add(h.useId()));
      evolutionBuckets.forEach(b -> seen.add(b.useId()));
      previousBuckets.forEach(b -> seen.add(b.useId()));
      nowToDate.forEach(b -> seen.add(b.useId()));
      beforeToDate.forEach(b -> seen.add(b.useId()));
      seen.removeAll(uses.keySet());
      if (!seen.isEmpty()) {
        facilities.usesById(seen).forEach(u -> uses.put(u.id(), u));
      }
      openMinutes();

      List<Point<Long>> bookingsEvolution = new ArrayList<>();
      List<Point<Double>> minutesEvolution = new ArrayList<>();
      List<Point<Long>> paidEvolution = new ArrayList<>();
      List<Point<Occupancy>> occupancyEvolution = new ArrayList<>();
      List<Point<Long>> peopleEvolution = new ArrayList<>();
      Map<LocalDate, List<UseBucket>> byStart =
          evolutionBuckets.stream().collect(Collectors.groupingBy(UseBucket::start));
      for (ReportPeriod p : evolution) {
        List<UseBucket> rows = byStart.getOrDefault(p.from(), List.of());
        bookingsEvolution.add(point(p, bookings(rows)));
        minutesEvolution.add(point(p, weighted(rows, UseBucket::useId, UseBucket::minutes)));
        paidEvolution.add(point(p, paid(rows)));
        occupancyEvolution.add(point(p, occupancy(reservedByFacility(rows), p.from(), p.to())));
        peopleEvolution.add(point(p, peopleByStart.getOrDefault(p.from(), 0L)));
      }

      Map<UUID, Double> reservedNow = new HashMap<>();
      for (UseDay d : days) {
        reservedNow.merge(d.facilityId(), weight(d.useId()).weigh(d.minutes()), Double::sum);
      }
      long bookingsNow = days.stream().mapToLong(UseDay::bookings).sum();
      double minutesNow = weighted(days, UseDay::useId, UseDay::minutes);
      long paidNow = money.stream().mapToLong(FacilityMoney::paidCents).sum();
      Occupancy occupancyNow = occupancy(reservedNow, period.from(), period.to());

      long bookingsBefore;
      double minutesBefore;
      long paidBefore;
      Occupancy occupancyBefore;
      long peopleBefore;
      if (custom) {
        bookingsBefore = bookings(previousBuckets);
        minutesBefore = weighted(previousBuckets, UseBucket::useId, UseBucket::minutes);
        paidBefore = paid(previousBuckets);
        occupancyBefore =
            occupancy(reservedByFacility(previousBuckets), previous.from(), previous.to());
        peopleBefore = previousPeople;
      } else {
        int before = evolution.size() - 2;
        bookingsBefore = bookingsEvolution.get(before).value();
        minutesBefore = minutesEvolution.get(before).value();
        paidBefore = paidEvolution.get(before).value();
        occupancyBefore = occupancyEvolution.get(before).value();
        peopleBefore = peopleEvolution.get(before).value();
      }

      Kpis kpis;
      if (comparison.toDate()) {
        kpis =
            new Kpis(
                new Kpi<>(
                    bookingsNow,
                    bookingsBefore,
                    bookings(nowToDate),
                    bookings(beforeToDate),
                    bookingsEvolution),
                new Kpi<>(
                    occupancyNow,
                    occupancyBefore,
                    occupancy(reservedByFacility(nowToDate), comparison.from(), comparison.to()),
                    occupancy(
                        reservedByFacility(beforeToDate),
                        comparison.previousFrom(),
                        comparison.previousTo()),
                    occupancyEvolution),
                new Kpi<>(
                    minutesNow,
                    minutesBefore,
                    weighted(nowToDate, UseBucket::useId, UseBucket::minutes),
                    weighted(beforeToDate, UseBucket::useId, UseBucket::minutes),
                    minutesEvolution),
                new Kpi<>(
                    people, peopleBefore, peopleNowToDate, peopleBeforeToDate, peopleEvolution),
                new Kpi<>(paidNow, paidBefore, paid(nowToDate), paid(beforeToDate), paidEvolution));
      } else {
        kpis =
            new Kpis(
                new Kpi<>(
                    bookingsNow, bookingsBefore, bookingsNow, bookingsBefore, bookingsEvolution),
                new Kpi<>(
                    occupancyNow,
                    occupancyBefore,
                    occupancyNow,
                    occupancyBefore,
                    occupancyEvolution),
                new Kpi<>(minutesNow, minutesBefore, minutesNow, minutesBefore, minutesEvolution),
                new Kpi<>(people, peopleBefore, people, peopleBefore, peopleEvolution),
                new Kpi<>(paidNow, paidBefore, paidNow, paidBefore, paidEvolution));
      }
      return new IndicatorsPanel(
          period,
          previous,
          comparison,
          kpis,
          occupancyByFacility(reservedNow),
          dailyOccupancy(days, breakdown),
          peakHours(hours),
          cancellations(cancellations, breakdown),
          revenue(money),
          people(monthlyPeople, firstMonth, months));
    }

    /**
     * As instalacións que poden abrir co filtro: nin de baixa, a escollida (se hai) e, co filtro de
     * uso, as que teñen algún dos usos.
     */
    private List<UUID> openable() {
      return facilityById.values().stream()
          .filter(f -> !f.withdrawn())
          .map(FacilitySummary::id)
          .filter(id -> filter.facilityId() == null || filter.facilityId().equals(id))
          .filter(
              id ->
                  filter.useIds().isEmpty()
                      || filter.selected().values().stream()
                          .anyMatch(u -> u.facilityId().equals(id)))
          .toList();
    }

    /**
     * Os tramos abertos de todo o que se mira (a evolución e o anterior) pasan a minutos por
     * instalación e día ({@link OpeningMinutes}); os do período, ademais, ás celas día × hora.
     */
    private void openMinutes() {
      List<UUID> ids = openable();
      if (ids.isEmpty()) {
        return;
      }
      LocalDate from = min(evolution.getFirst().from(), previous.from());
      LocalDate to = max(evolution.getLast().to(), period.to());
      Map<UUID, List<OpeningInterval>> intervals =
          facilities.longOpeningIntervalsByFacility(filter.facilityId(), from, to);
      List<TimeSpan> inPeriod = new ArrayList<>();
      for (UUID id : ids) {
        List<UseSummary> selected =
            filter.selected().values().stream().filter(u -> u.facilityId().equals(id)).toList();
        List<TimeSpan> spans = new ArrayList<>();
        for (OpeningInterval i : intervals.getOrDefault(id, List.of())) {
          if (selected.isEmpty() || selected.stream().anyMatch(u -> usedOn(u, i.date()))) {
            TimeSpan span = new TimeSpan(i.start(), i.end());
            spans.add(span);
            if (period.contains(i.date())) {
              inPeriod.add(span);
            }
          }
        }
        open.put(id, new TreeMap<>(OpeningMinutes.of(spans).perDay()));
      }
      openInPeriod = OpeningMinutes.of(inPeriod);
    }

    private long openMinutes(UUID facility, LocalDate from, LocalDate to) {
      NavigableMap<LocalDate, Long> perDay = open.get(facility);
      if (perDay == null) {
        return 0;
      }
      long total = 0;
      for (long minutes : perDay.subMap(from, true, to, true).values()) {
        total += minutes;
      }
      return total;
    }

    /** A ocupación do tramo: só as instalacións que abren nel, cada unha sen pasar do 100 %. */
    private Occupancy occupancy(Map<UUID, Double> reserved, LocalDate from, LocalDate to) {
      double part = 0;
      long whole = 0;
      for (UUID id : open.keySet()) {
        long minutes = openMinutes(id, from, to);
        if (minutes > 0) {
          whole += minutes;
          part += new Ratio(reserved.getOrDefault(id, 0.0), minutes).cappedPart();
        }
      }
      return new Occupancy(part, whole);
    }

    private Map<UUID, Double> reservedByFacility(Collection<UseBucket> rows) {
      Map<UUID, Double> result = new HashMap<>();
      for (UseBucket b : rows) {
        UseSummary use = uses.get(b.useId());
        if (use != null) {
          result.merge(use.facilityId(), weight(b.useId()).weigh(b.minutes()), Double::sum);
        }
      }
      return result;
    }

    private List<FacilityOccupancy> occupancyByFacility(Map<UUID, Double> reserved) {
      List<FacilityOccupancy> result = new ArrayList<>();
      for (UUID id : open.keySet()) {
        long minutes = openMinutes(id, period.from(), period.to());
        if (minutes > 0) {
          result.add(
              new FacilityOccupancy(
                  id,
                  facilityById.get(id).name(),
                  new Occupancy(
                      new Ratio(reserved.getOrDefault(id, 0.0), minutes).cappedPart(), minutes)));
        }
      }
      return Names.sorted(result, FacilityOccupancy::name);
    }

    private List<BucketOccupancy> dailyOccupancy(List<UseDay> days, PeriodUnit unit) {
      List<BucketOccupancy> result = new ArrayList<>();
      for (LocalDate start : period.bucketStarts(unit)) {
        LocalDate from = max(start, period.from());
        LocalDate to = min(unit.end(start), period.to());
        Map<UUID, Double> reserved = new HashMap<>();
        for (UseDay d : days) {
          if (!d.day().isBefore(from) && !d.day().isAfter(to)) {
            reserved.merge(d.facilityId(), weight(d.useId()).weigh(d.minutes()), Double::sum);
          }
        }
        result.add(
            new BucketOccupancy(from, to, occupancy(reserved, from, to), inProgress(from, to)));
      }
      return result;
    }

    /** As 168 celas: minutos reservados (só das instalacións que abren) e abertos. */
    private List<PeakHour> peakHours(List<UseWeekdayHour> hours) {
      double[] reservedCells = new double[DayOfWeek.values().length * HOURS];
      for (UseWeekdayHour h : hours) {
        UseSummary use = uses.get(h.useId());
        if (use != null && open.containsKey(use.facilityId())) {
          reservedCells[(h.isoWeekday() - 1) * HOURS + h.hour()] +=
              weight(h.useId()).weigh(h.minutes());
        }
      }
      List<PeakHour> result = new ArrayList<>(reservedCells.length);
      for (DayOfWeek day : DayOfWeek.values()) {
        for (int hour = 0; hour < HOURS; hour++) {
          long whole = openInPeriod.at(day, hour);
          double part = reservedCells[(day.getValue() - 1) * HOURS + hour];
          result.add(
              new PeakHour(
                  day.getValue(), hour, new Occupancy(new Ratio(part, whole).cappedPart(), whole)));
        }
      }
      return result;
    }

    private List<Cancellations> cancellations(List<CancellationBucket> rows, PeriodUnit unit) {
      Map<LocalDate, CancellationBucket> byStart = new HashMap<>();
      rows.forEach(r -> byStart.put(r.start(), r));
      List<Cancellations> result = new ArrayList<>();
      for (LocalDate start : period.bucketStarts(unit)) {
        CancellationBucket row = byStart.get(start);
        LocalDate from = max(start, period.from());
        LocalDate to = min(unit.end(start), period.to());
        result.add(
            new Cancellations(
                from,
                to,
                row == null ? 0 : row.inTime(),
                row == null ? 0 : row.late(),
                row == null ? 0 : row.byMunicipality(),
                inProgress(from, to)));
      }
      return result;
    }

    private List<FacilityRevenue> revenue(List<FacilityMoney> money) {
      List<FacilityRevenue> result = new ArrayList<>();
      for (FacilityMoney m : money) {
        FacilitySummary facility = facilityById.get(m.facilityId());
        result.add(
            new FacilityRevenue(
                m.facilityId(),
                facility == null ? "" : facility.name(),
                m.paidCents(),
                m.pendingCents(),
                m.toRefundCents(),
                m.refundedCents()));
      }
      return Names.sorted(result, FacilityRevenue::name);
    }

    private List<People> people(List<PeopleBucket> rows, LocalDate firstMonth, int months) {
      Map<LocalDate, Long> byStart = peopleByStart(rows);
      List<People> result = new ArrayList<>(months);
      for (int i = 0; i < months; i++) {
        LocalDate start = PeriodUnit.MONTH.plus(firstMonth, i);
        LocalDate end = PeriodUnit.MONTH.end(start);
        result.add(new People(start, end, byStart.getOrDefault(start, 0L), inProgress(start, end)));
      }
      return result;
    }

    /** Un punto da evolución; en curso se contén hoxe. */
    private <T> Point<T> point(ReportPeriod p, T value) {
      return new Point<>(p.from(), p.to(), value, p.contains(today));
    }

    private boolean inProgress(LocalDate from, LocalDate to) {
      return ReportPeriod.inProgress(from, to, today);
    }

    private UseWeight weight(UUID useId) {
      UseSummary use = uses.get(useId);
      return use == null
          ? UseWeight.WHOLE
          : UseWeight.of(use.rentalMode(), use.parts().size(), use.capacity());
    }

    private <R> double weighted(
        Collection<R> rows, Function<R, UUID> use, ToLongFunction<R> minutes) {
      double total = 0;
      for (R row : rows) {
        total += weight(use.apply(row)).weigh(minutes.applyAsLong(row));
      }
      return total;
    }
  }

  private static long bookings(Collection<UseBucket> rows) {
    return rows.stream().mapToLong(UseBucket::bookings).sum();
  }

  private static long paid(Collection<UseBucket> rows) {
    return rows.stream().mapToLong(UseBucket::paidCents).sum();
  }

  private static Map<LocalDate, Long> peopleByStart(List<PeopleBucket> rows) {
    Map<LocalDate, Long> result = new HashMap<>();
    rows.forEach(r -> result.put(r.start(), r.people()));
    return result;
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }

  private static LocalDate max(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }
}
