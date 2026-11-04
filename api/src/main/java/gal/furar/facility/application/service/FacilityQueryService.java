// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilityPaymentTexts;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.facility.UseTerms;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.PhotoUrlSignature;
import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePrice;
import gal.furar.facility.domain.UseRules;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.OpeningHours;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link FacilityQueries}) para outros módulos. Sempre no
 * concello actual (o repositorio falla sen el); sen permisos propios porque non é un caso de uso.
 */
@Service
class FacilityQueryService implements FacilityQueries {

  private final FacilityRepository repository;
  private final FacilityLock lock;
  private final FacilityUseRepository uses;
  private final SeasonRepository seasons;
  private final ScheduleExceptionRepository exceptions;
  private final PublicHolidayRepository holidays;
  private final BlockRepository blocks;
  private final FacilityDrafts drafts;
  private final FacilityGalleries galleries;

  @Autowired
  FacilityQueryService(
      FacilityRepository repository,
      FacilityLock lock,
      FacilityUseRepository uses,
      SeasonRepository seasons,
      ScheduleExceptionRepository exceptions,
      PublicHolidayRepository holidays,
      BlockRepository blocks,
      FacilityGalleries galleries,
      ObjectProvider<RequirementProbe> probes) {
    this.repository = repository;
    this.galleries = galleries;
    this.drafts = new FacilityDrafts(probes.orderedStream().toList());
    this.lock = lock;
    this.uses = uses;
    this.seasons = seasons;
    this.exceptions = exceptions;
    this.holidays = holidays;
    this.blocks = blocks;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FacilitySummary> find(UUID id) {
    return repository.findById(new FacilityId(id)).map(FacilityQueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FacilityPaymentTexts> paymentTexts(UUID id) {
    return repository
        .findById(new FacilityId(id))
        .map(
            f ->
                new FacilityPaymentTexts(
                    text(f.paymentNotes().payment()), text(f.paymentNotes().refund())));
  }

  private static FacilityPaymentTexts.@Nullable Text text(@Nullable LocalizedText t) {
    return t == null ? null : new FacilityPaymentTexts.Text(t.gl(), t.es());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FacilitySummary> findPublic(UUID id) {
    return repository
        .findById(new FacilityId(id))
        .filter(f -> f.status() != FacilityStatus.WITHDRAWN)
        .filter(Facility::published)
        .filter(f -> drafts.missing(f.id().value()).isEmpty())
        .map(FacilityQueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FacilitySummary> summary(List<UUID> ids) {
    return repository.findAllById(ids.stream().map(FacilityId::new).toList()).stream()
        .map(FacilityQueryService::summary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FacilitySummary> all() {
    return repository.findAll().stream().map(FacilityQueryService::summary).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UseSummary> allUses() {
    return uses.findAllInMunicipality().stream().map(FacilityQueryService::summary).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FacilityCover> covers(Collection<UUID> facilityIds) {
    if (facilityIds.isEmpty()) {
      return List.of();
    }
    List<Facility> found =
        repository.findAllById(facilityIds.stream().distinct().map(FacilityId::new).toList());
    String base =
        "/api/v1/municipalities/" + Objects.requireNonNull(TenantContext.require().slug(), "slug");
    return galleries.of(found).entrySet().stream()
        .map(e -> cover(e.getKey().value(), e.getValue().cover(), base))
        .toList();
  }

  /** As mesmas rutas dos bytes ca a ficha da xestión ({@code ImageMapper}), coa firma. */
  private static FacilityCover cover(UUID facilityId, ImageView view, String base) {
    boolean photo = view.ref() instanceof ImageRef.OfPhoto;
    String id =
        switch (view.ref()) {
          case ImageRef.OfPhoto p -> p.id().value().toString();
          case ImageRef.OfDrawing d -> d.code();
        };
    String path = base + (photo ? "/photos/" : "/drawings/") + id + "/";
    // A reserva vese aínda que a instalación saia do público: a foto leva a firma (ADR-0024).
    String query =
        photo
            ? "?"
                + PhotoUrlSignature.PARAM
                + "="
                + PhotoUrlSignature.sign(
                    TenantContext.require().id(), UUID.fromString(id), Instant.now())
            : "";
    return new FacilityCover(
        facilityId,
        photo ? FacilityCover.Kind.PHOTO : FacilityCover.Kind.DRAWING,
        id,
        new FacilityCover.Text(view.alt().gl(), view.alt().es()),
        view.width(),
        view.height(),
        path + "large" + query,
        path + "thumb" + query,
        view.createdAt(),
        view.name() == null ? null : new FacilityCover.Text(view.name().gl(), view.name().es()));
  }

  /** Só dentro da transacción de quen chama: o bloqueo dura ata o seu commit. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean lockFacility(UUID id) {
    return lock.lock(new FacilityId(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<OpeningInterval> openingIntervals(UUID facilityId, LocalDate from, LocalDate to) {
    checkRange(from, to, MAX_OPENING_DAYS);
    FacilityId id = new FacilityId(facilityId);
    if (repository.findById(id).isEmpty()) {
      return List.of();
    }
    Set<LocalDate> holidayDates =
        holidays.findBetween(from, to).stream()
            .map(PublicHoliday::date)
            .collect(Collectors.toUnmodifiableSet());
    return new OpeningHours(
            seasons.findByFacility(id), exceptions.findBetween(id, from, to), holidayDates)
        .slots(from, to, OpeningHours.ZONE, blocks.findBetween(id, from, to)).stream()
            .map(
                s ->
                    new OpeningInterval(
                        s.date(),
                        s.range().startMinute(),
                        s.range().endMinute(),
                        s.start(),
                        s.end(),
                        s.alignFromMinute()))
            .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<OpeningInterval>> openingIntervalsByFacility(
      @Nullable UUID facilityId, LocalDate from, LocalDate to) {
    checkRange(from, to, MAX_OPENING_DAYS);
    return intervalsByFacility(facilityId, from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<OpeningInterval>> longOpeningIntervalsByFacility(
      @Nullable UUID facilityId, LocalDate from, LocalDate to) {
    checkRange(from, to, MAX_LONG_OPENING_DAYS);
    return intervalsByFacility(facilityId, from, to);
  }

  private static void checkRange(LocalDate from, LocalDate to, int maxDays) {
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= maxDays) {
      throw new IllegalArgumentException("from " + from + " to " + to);
    }
  }

  /** Cinco lecturas fixas (instalacións, festivos, tempadas, excepcións e bloqueos). */
  private Map<UUID, List<OpeningInterval>> intervalsByFacility(
      @Nullable UUID facilityId, LocalDate from, LocalDate to) {
    List<FacilityId> ids =
        (facilityId == null
                ? repository.findAll().stream().filter(f -> f.status() != FacilityStatus.WITHDRAWN)
                : repository.findById(new FacilityId(facilityId)).stream())
            .map(Facility::id)
            .toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    Set<LocalDate> holidayDates =
        holidays.findBetween(from, to).stream()
            .map(PublicHoliday::date)
            .collect(Collectors.toUnmodifiableSet());
    Map<FacilityId, List<Season>> seasonsOf =
        seasons.findByFacilities(ids).stream().collect(Collectors.groupingBy(Season::facilityId));
    Map<FacilityId, List<ScheduleException>> exceptionsOf =
        exceptions.findBetween(ids, from, to).stream()
            .collect(Collectors.groupingBy(ScheduleException::facilityId));
    Map<FacilityId, List<Block>> blocksOf =
        blocks.findBetween(ids, from, to).stream()
            .collect(Collectors.groupingBy(Block::facilityId));
    Map<UUID, List<OpeningInterval>> result = new LinkedHashMap<>();
    for (FacilityId id : ids) {
      result.put(
          id.value(),
          new OpeningHours(
                  seasonsOf.getOrDefault(id, List.of()),
                  exceptionsOf.getOrDefault(id, List.of()),
                  holidayDates)
              .slots(from, to, OpeningHours.ZONE, blocksOf.getOrDefault(id, List.of())).stream()
                  .map(
                      s ->
                          new OpeningInterval(
                              s.date(),
                              s.range().startMinute(),
                              s.range().endMinute(),
                              s.start(),
                              s.end(),
                              s.alignFromMinute()))
                  .toList());
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> exclusiveUseOn(UUID facilityId, LocalDate date) {
    return uses.findActive(new FacilityId(facilityId)).stream()
        .filter(FacilityUse::blocksOthers)
        .filter(u -> u.availableOn(date))
        .map(u -> u.id().value())
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UseSummary> use(UUID useId) {
    return uses.findById(new UseId(useId)).map(FacilityQueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UseSummary> usesById(Collection<UUID> useIds) {
    if (useIds.isEmpty()) {
      return List.of();
    }
    return uses.findAllById(useIds.stream().distinct().map(UseId::new).toList()).stream()
        .map(FacilityQueryService::summary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UseSummary> uses(UUID facilityId) {
    return uses.findActive(new FacilityId(facilityId)).stream()
        .map(FacilityQueryService::summary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> visibleIds(int page, int size) {
    if (page < 0 || size < 1 || size > FacilityService.MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("page " + page + ", size " + size);
    }
    // Publicadas, non de baixa e con reserva por internet, filtradas na base (ADR-0022, punto
    // 17); o borrador, por lote en cada páxina lida. Le só ata encher a páxina pedida.
    long wanted = (long) (page + 1) * size;
    List<UUID> visible = new ArrayList<>();
    for (int p = 0; visible.size() < wanted; p++) {
      List<Facility> items =
          repository.findPublishedPage(p, FacilityService.MAX_PAGE_SIZE, Language.GL, true);
      visible.addAll(visible(items));
      if (items.size() < FacilityService.MAX_PAGE_SIZE) {
        break;
      }
    }
    int from = (int) Math.min((long) page * size, visible.size());
    return List.copyOf(visible.subList(from, (int) Math.min(wanted, visible.size())));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> visibleIds(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Set<UUID> visible =
        Set.copyOf(visible(repository.findAllById(ids.stream().map(FacilityId::new).toList())));
    return ids.stream().filter(visible::contains).distinct().toList();
  }

  /**
   * Nin de baixa nin sen publicar nin sen reserva por internet nin en borrador (por lote), na orde
   * dada.
   */
  private List<UUID> visible(List<Facility> facilities) {
    List<UUID> candidates =
        facilities.stream()
            .filter(f -> f.status() != FacilityStatus.WITHDRAWN)
            .filter(Facility::published)
            .filter(f -> f.details().onlineBooking())
            .map(f -> f.id().value())
            .toList();
    Map<UUID, Set<Requirement>> missing = drafts.missing(candidates);
    return candidates.stream().filter(id -> missing.get(id).isEmpty()).toList();
  }

  private static UseSummary summary(FacilityUse u) {
    UsePrice price = u.price();
    UseRules rules = u.rules();
    return new UseSummary(
        u.id().value(),
        u.facilityId().value(),
        u.name(),
        u.rentalMode().name(),
        u.capacity(),
        u.slotMinutes(),
        u.blocksOthers(),
        u.parts().stream().map(p -> new UseSummary.Part(p.id(), p.name())).toList(),
        u.periods().stream().map(p -> new UseSummary.DateRange(p.from(), p.to())).toList(),
        u.weekdays(),
        u.isArchived(),
        new UseTerms(
            new UseTerms.Price(
                price.kind().name(),
                price.amountCents(),
                u.rentalMode() == RentalMode.SEATS ? "PERSON" : "BOOKING"),
            new UseTerms.Rules(
                rules.minAdvanceHours(),
                rules.maxAdvanceDays(),
                rules.maxSlots(),
                rules.capCount(),
                rules.capPeriod() == null ? null : rules.capPeriod().name(),
                rules.cancelHours())));
  }

  private static FacilitySummary summary(Facility facility) {
    Address a = facility.address();
    return new FacilitySummary(
        facility.id().value(),
        facility.name(),
        facility.status().name(),
        facility.details().onlineBooking(),
        facility.details().showFreeSlots(),
        a == null
            ? null
            : new FacilitySummary.Address(
                a.street(), a.streetNumber(), a.postalCode(), a.locality()));
  }
}
