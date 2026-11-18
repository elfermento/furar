// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.FacilityOpenDataQueries;
import gal.furar.facility.PublicCustomFields;
import gal.furar.facility.PublicFacilityData;
import gal.furar.facility.application.port.out.FacilityCustomFields;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UseRules;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.Closure;
import gal.furar.facility.domain.schedule.OpeningHours;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import java.text.Collator;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa {@link FacilityOpenDataQueries} (ADR-0034, punto 1): as mesmas regras ca o listado e a
 * ficha públicos, para moitas instalacións á vez e cun número fixo de lecturas (instalacións por
 * páxinas, borradores, deportes, usos, tempadas e campos; nos peches, tamén festivos, excepcións e
 * bloqueos).
 */
@Service
class FacilityOpenDataService implements FacilityOpenDataQueries {

  /** Tamaño de cada páxina lida do repositorio. */
  private static final int PAGE = FacilityService.MAX_PAGE_SIZE;

  /** O día enteiro en minutos: un bloqueo así pecha todo o día. */
  private static final TimeRange WHOLE_DAY = new TimeRange(0, 24 * 60);

  private final FacilityRepository repository;
  private final FacilityDrafts drafts;
  private final SportRepository sports;
  private final FacilityUseRepository uses;
  private final SeasonRepository seasons;
  private final ScheduleExceptionRepository exceptions;
  private final PublicHolidayRepository holidays;
  private final BlockRepository blocks;
  private final FacilityCustomFields customFields;

  @Autowired
  FacilityOpenDataService(
      FacilityRepository repository,
      ObjectProvider<RequirementProbe> probes,
      SportRepository sports,
      FacilityUseRepository uses,
      SeasonRepository seasons,
      ScheduleExceptionRepository exceptions,
      PublicHolidayRepository holidays,
      BlockRepository blocks,
      FacilityCustomFields customFields) {
    this.repository = repository;
    this.drafts = new FacilityDrafts(probes.orderedStream().toList());
    this.sports = sports;
    this.uses = uses;
    this.seasons = seasons;
    this.exceptions = exceptions;
    this.holidays = holidays;
    this.blocks = blocks;
    this.customFields = customFields;
  }

  @Override
  @Transactional(readOnly = true)
  public PublicFacilityData publicData(LocalDate today) {
    List<Facility> visible = visible();
    PublicCustomFields fields =
        customFields.publicFields(visible.stream().map(Facility::id).toList());
    if (visible.isEmpty()) {
      return new PublicFacilityData(List.of(), fields.definitions());
    }
    Map<SportRef, Sport> named = SportNames.of(sports);
    Set<FacilityId> ids = visible.stream().map(Facility::id).collect(Collectors.toSet());
    Map<FacilityId, List<FacilityUse>> usesOf =
        uses.findAllInMunicipality().stream()
            .filter(u -> ids.contains(u.facilityId()))
            .filter(u -> !u.isArchived())
            .filter(u -> PublicFacilityDetailService.stillBookable(u, today))
            .collect(Collectors.groupingBy(FacilityUse::facilityId));
    Map<FacilityId, List<Season>> seasonsOf =
        seasons.findByFacilities(List.copyOf(ids)).stream()
            .collect(Collectors.groupingBy(Season::facilityId));
    Collator collator = Collator.getInstance(Locale.forLanguageTag("gl"));
    List<PublicFacilityData.Item> items = new ArrayList<>();
    for (Facility f : visible) {
      List<FacilityUse> shown =
          usesOf.getOrDefault(f.id(), List.of()).stream()
              .sorted(
                  Comparator.comparing(FacilityUse::name, collator)
                      .thenComparing(u -> u.id().value()))
              .toList();
      items.add(
          item(
              f,
              SportNames.resolve(named, f),
              shown,
              week(seasonsOf.getOrDefault(f.id(), List.of()), today),
              fields.of(f.id().value())));
    }
    items.sort(
        Comparator.comparing(PublicFacilityData.Item::name, collator)
            .thenComparing(PublicFacilityData.Item::id));
    return new PublicFacilityData(items, fields.definitions());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClosureDay> publicClosures(LocalDate from, LocalDate to) {
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_CLOSURE_DAYS) {
      throw new IllegalArgumentException("from " + from + " to " + to);
    }
    List<Facility> visible = visible();
    if (visible.isEmpty()) {
      return List.of();
    }
    List<FacilityId> ids = visible.stream().map(Facility::id).toList();
    List<PublicHoliday> holidayList = holidays.findBetween(from, to);
    Set<LocalDate> holidayDates =
        holidayList.stream().map(PublicHoliday::date).collect(Collectors.toUnmodifiableSet());
    Map<FacilityId, List<Season>> seasonsOf =
        seasons.findByFacilities(ids).stream().collect(Collectors.groupingBy(Season::facilityId));
    Map<FacilityId, List<ScheduleException>> exceptionsOf =
        exceptions.findBetween(ids, from, to).stream()
            .collect(Collectors.groupingBy(ScheduleException::facilityId));
    Map<FacilityId, List<Block>> blocksOf =
        blocks.findBetween(ids, from, to).stream()
            .filter(b -> b.interval().equals(WHOLE_DAY))
            .collect(Collectors.groupingBy(Block::facilityId));
    List<ClosureDay> result = new ArrayList<>();
    for (FacilityId id : ids) {
      List<ScheduleException> exceptionList = exceptionsOf.getOrDefault(id, List.of());
      OpeningHours hours =
          new OpeningHours(seasonsOf.getOrDefault(id, List.of()), exceptionList, holidayDates);
      for (Closure c :
          Closure.between(
              hours, holidayList, exceptionList, blocksOf.getOrDefault(id, List.of()), from, to)) {
        // Os festivos sen excepción van como «festivo»; as horas soltas non son un día pechado.
        boolean holidayOnly =
            c.holiday() != null && c.reason() == null && !closedBy(c, exceptionList);
        if (c.hours() != null || holidayOnly) {
          continue;
        }
        for (LocalDate d = c.from(); !d.isAfter(c.to()); d = d.plusDays(1)) {
          result.add(new ClosureDay(d, id.value(), c.reason()));
        }
      }
    }
    result.sort(Comparator.comparing(ClosureDay::date).thenComparing(ClosureDay::facilityId));
    return result;
  }

  /** Unha excepción pechada nese día (un festivo pode coincidir cunha, sen motivo). */
  private static boolean closedBy(Closure c, List<ScheduleException> exceptionList) {
    return exceptionList.stream().anyMatch(e -> e.date().equals(c.from()) && !e.isOpen());
  }

  @Override
  @Transactional(readOnly = true)
  public List<HolidayDay> holidays(LocalDate from, LocalDate to) {
    return holidays.findBetween(from, to).stream()
        .sorted(Comparator.comparing(PublicHoliday::date))
        .map(h -> new HolidayDay(h.date(), h.nameGl(), h.nameEs()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ArchivedPart> archivedParts(Collection<UUID> useIds) {
    return uses.findArchivedParts(useIds.stream().map(UseId::new).toList()).stream()
        .map(p -> new ArchivedPart(p.useId().value(), p.id(), p.archivedAt()))
        .toList();
  }

  /** As do listado público: publicadas, nin de baixa nin en borrador (ADR-0022, punto 3). */
  private List<Facility> visible() {
    List<Facility> candidates = new ArrayList<>();
    for (int page = 0; ; page++) {
      List<Facility> items = repository.findPublishedPage(page, PAGE, Language.GL, false);
      candidates.addAll(items);
      if (items.size() < PAGE) {
        break;
      }
    }
    if (candidates.isEmpty()) {
      return List.of();
    }
    Map<UUID, Set<Requirement>> missing =
        drafts.missing(candidates.stream().map(f -> f.id().value()).toList());
    return candidates.stream()
        .filter(f -> f.status() != FacilityStatus.WITHDRAWN)
        .filter(f -> missing.get(f.id().value()).isEmpty())
        .toList();
  }

  /** A semana da tempada que cobre hoxe; nula se ningunha a cobre. */
  private static @Nullable Map<Integer, List<PublicFacilityData.Hours>> week(
      List<Season> seasonList, LocalDate today) {
    return seasonList.stream()
        .filter(s -> s.covers(today))
        .findFirst()
        .map(
            s -> {
              Map<Integer, List<PublicFacilityData.Hours>> days = new LinkedHashMap<>();
              for (ScheduleDay day : ScheduleDay.values()) {
                days.put(
                    day.number(),
                    s.week().hours(day).stream()
                        .map(r -> new PublicFacilityData.Hours(r.startMinute(), r.endMinute()))
                        .toList());
              }
              return days;
            })
        .orElse(null);
  }

  private static PublicFacilityData.Item item(
      Facility f,
      List<Sport> sportList,
      List<FacilityUse> shown,
      @Nullable Map<Integer, List<PublicFacilityData.Hours>> week,
      Map<String, Object> values) {
    Address a = Objects.requireNonNull(f.address(), "published facility without address");
    return new PublicFacilityData.Item(
        f.id().value(),
        f.name(),
        f.description() == null
            ? null
            : new PublicFacilityData.Text(f.description().gl(), f.description().es()),
        a.street(),
        a.streetNumber(),
        a.postalCode(),
        a.locality(),
        a.latitude(),
        a.longitude(),
        f.details().phone(),
        f.details().email(),
        sportList.stream()
            .map(s -> new PublicFacilityData.SportItem(s.ref().code(), text(s.label())))
            .toList(),
        f.details().covered(),
        f.amenities().stream().sorted().map(Amenity::name).toList(),
        f.status() == FacilityStatus.TEMPORARILY_CLOSED,
        f.details().onlineBooking(),
        f.details().showFreeSlots(),
        week,
        f.houseRules() == null
            ? null
            : new PublicFacilityData.Text(f.houseRules().gl(), f.houseRules().es()),
        values,
        shown.stream().map(FacilityOpenDataService::use).toList());
  }

  private static PublicFacilityData.Use use(FacilityUse u) {
    UseRules r = u.rules();
    return new PublicFacilityData.Use(
        u.id().value(),
        u.name(),
        u.rentalMode().name(),
        u.slotMinutes(),
        u.parts().stream().map(p -> p.name()).toList(),
        u.capacity(),
        u.weekdays(),
        u.availability() == UseAvailability.DATES
            ? u.periods().stream()
                .map(p -> new PublicFacilityData.Period(p.from(), p.to()))
                .toList()
            : List.of(),
        u.price().kind().name(),
        u.price().amountCents(),
        r.minAdvanceHours(),
        r.maxAdvanceDays(),
        r.maxSlots(),
        r.capCount(),
        r.capPeriod() == null ? null : r.capPeriod().name(),
        r.cancelHours());
  }

  private static PublicFacilityData.Text text(LocalizedText t) {
    return new PublicFacilityData.Text(t.gl(), t.es());
  }
}
