// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UseError;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePart;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA dos usos, sempre dentro dun concello (A7b). Hibernate executa os INSERT antes ca os
 * DELETE nun mesmo flush (§1.2.15 do plan): por iso os períodos vellos bórranse e fanse flush antes
 * de inserir os novos. Os nomes das partes van nunha restrición diferida, que se comproba ao final
 * do gardado ({@link UsePartNameCheck}) para traducir o erro.
 */
@Component
class FacilityUsePersistenceAdapter extends TenantScopedPersistenceAdapter
    implements FacilityUseRepository {

  /** Índice único do nome do uso na instalación (migración {@code facility_uses}). */
  static final String NAME_UNIQUE_INDEX = "facility_use_name_uk";

  /** Exclusión diferida do nome das partes no uso. */
  static final String PART_NAME_EXCLUSION = "facility_use_part_name_ex";

  /** Exclusión dos períodos solapados dun uso (o dominio xa o impide). */
  static final String PERIOD_EXCLUSION = "facility_use_period_overlap_ex";

  private final FacilityUseJpaRepository uses;
  private final FacilityUsePartJpaRepository parts;
  private final FacilityUsePeriodJpaRepository periods;
  private final UsePartNameCheck partNames;

  FacilityUsePersistenceAdapter(
      FacilityUseJpaRepository uses,
      FacilityUsePartJpaRepository parts,
      FacilityUsePeriodJpaRepository periods,
      UsePartNameCheck partNames) {
    this.uses = uses;
    this.parts = parts;
    this.periods = periods;
    this.partNames = partNames;
  }

  @Override
  public List<FacilityUse> findActive(FacilityId facilityId) {
    tenant();
    return toDomain(
        uses.findByFacilityIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(facilityId.value()));
  }

  @Override
  public List<FacilityUse> findAll(FacilityId facilityId) {
    tenant();
    return toDomain(uses.findByFacilityIdOrderByCreatedAtAscIdAsc(facilityId.value()));
  }

  @Override
  public List<FacilityUse> findAllInMunicipality() {
    tenant();
    return toDomain(uses.findAll(Sort.by("createdAt", "id")));
  }

  @Override
  public List<ArchivedUsePart> findArchivedParts(FacilityId facilityId) {
    tenant();
    List<UUID> ids =
        uses.findByFacilityIdOrderByCreatedAtAscIdAsc(facilityId.value()).stream()
            .map(FacilityUseEntity::getId)
            .toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    return parts.findByUseIdInAndArchivedAtIsNotNull(ids).stream()
        .sorted(
            Comparator.comparingInt((FacilityUsePartEntity p) -> ids.indexOf(p.useId()))
                .thenComparingInt(FacilityUsePartEntity::position)
                .thenComparing(FacilityUsePartEntity::getId))
        .map(
            p ->
                new ArchivedUsePart(
                    p.getId(),
                    new UseId(p.useId()),
                    p.name(),
                    p.position(),
                    Objects.requireNonNull(p.archivedAt())))
        .toList();
  }

  @Override
  public void insertArchivedParts(List<ArchivedUsePart> archived) {
    tenant();
    for (ArchivedUsePart part : archived) {
      FacilityUsePartEntity entity =
          new FacilityUsePartEntity(part.id(), part.useId().value(), part.name(), part.position());
      entity.archive(part.archivedAt());
      parts.save(entity);
    }
    parts.flush();
  }

  @Override
  public Optional<FacilityUse> findById(FacilityId facilityId, UseId useId) {
    tenant();
    return uses.findByIdAndFacilityId(useId.value(), facilityId.value())
        .map(e -> toDomain(List.of(e)).getFirst());
  }

  @Override
  public Optional<FacilityUse> findById(UseId useId) {
    tenant();
    return uses.findById(useId.value()).map(e -> toDomain(List.of(e)).getFirst());
  }

  @Override
  public List<FacilityUse> findAllById(Collection<UseId> ids) {
    tenant();
    if (ids.isEmpty()) {
      return List.of();
    }
    return toDomain(uses.findAllById(ids.stream().map(UseId::value).toList()));
  }

  @Override
  public FacilityUse insert(FacilityUse use) {
    tenant();
    return translated(
        use,
        () -> {
          FacilityUseEntity entity =
              new FacilityUseEntity(use.id().value(), use.facilityId().value(), use.createdAt());
          apply(entity, use);
          uses.saveAndFlush(entity);
          List<UsePart> list = use.parts();
          for (int i = 0; i < list.size(); i++) {
            UsePart part = list.get(i);
            parts.save(new FacilityUsePartEntity(part.id(), use.id().value(), part.name(), i));
          }
          insertPeriods(use);
          parts.flush();
          partNames.checkNow();
          return reload(use);
        });
  }

  @Override
  public FacilityUse update(FacilityUse use) {
    tenant();
    FacilityUseEntity entity =
        uses.findByIdAndFacilityId(use.id().value(), use.facilityId().value())
            .orElseThrow(() -> new DomainException(UseError.NOT_FOUND));
    if (!Objects.equals(entity.version(), use.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return translated(
        use,
        () -> {
          // Primeiro os DELETE dos períodos, cun flush propio: se non, irían despois dos INSERT.
          periods.deleteAll(periods.findByUseIdInOrderByFromDateAsc(List.of(use.id().value())));
          periods.flush();
          Map<UUID, FacilityUsePartEntity> alive =
              parts
                  .findByUseIdInAndArchivedAtIsNullOrderByPositionAsc(List.of(use.id().value()))
                  .stream()
                  .collect(Collectors.toMap(FacilityUsePartEntity::getId, p -> p));
          List<UsePart> list = use.parts();
          for (int i = 0; i < list.size(); i++) {
            UsePart part = list.get(i);
            FacilityUsePartEntity existing = alive.remove(part.id());
            if (existing == null) {
              parts.save(new FacilityUsePartEntity(part.id(), use.id().value(), part.name(), i));
            } else {
              existing.apply(part.name(), i);
            }
          }
          alive.values().forEach(p -> p.archive(use.updatedAt()));
          insertPeriods(use);
          apply(entity, use);
          uses.saveAndFlush(entity);
          partNames.checkNow();
          return reload(use);
        });
  }

  private void insertPeriods(FacilityUse use) {
    for (UsePeriod period : use.periods()) {
      periods.save(
          new FacilityUsePeriodEntity(Ids.newV7(), use.id().value(), period.from(), period.to()));
    }
  }

  private FacilityUse reload(FacilityUse use) {
    return findById(use.facilityId(), use.id())
        .orElseThrow(() -> new DomainException(UseError.NOT_FOUND));
  }

  private static void apply(FacilityUseEntity entity, FacilityUse use) {
    entity.apply(
        use.name(),
        use.rentalMode(),
        use.capacity(),
        use.slotMinutes(),
        use.availability(),
        use.blocksOthers(),
        use.archivedAt(),
        use.updatedAt());
    entity.applyTerms(use.weekdays(), use.price(), use.rules());
  }

  /** Usos coas súas partes vivas e os seus períodos: tres consultas para toda a lista. */
  private List<FacilityUse> toDomain(List<FacilityUseEntity> entities) {
    if (entities.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = entities.stream().map(FacilityUseEntity::getId).toList();
    Map<UUID, List<UsePart>> partsByUse = new HashMap<>();
    for (FacilityUsePartEntity p : parts.findByUseIdInAndArchivedAtIsNullOrderByPositionAsc(ids)) {
      partsByUse
          .computeIfAbsent(p.useId(), k -> new ArrayList<>())
          .add(new UsePart(p.getId(), p.name()));
    }
    Map<UUID, List<UsePeriod>> periodsByUse = new HashMap<>();
    for (FacilityUsePeriodEntity p : periods.findByUseIdInOrderByFromDateAsc(ids)) {
      periodsByUse
          .computeIfAbsent(p.useId(), k -> new ArrayList<>())
          .add(new UsePeriod(p.fromDate(), p.toDate()));
    }
    return entities.stream()
        .map(
            e ->
                FacilityUse.rehydrate(
                    new UseId(e.getId()),
                    new FacilityId(e.facilityId()),
                    e.name(),
                    e.rentalMode(),
                    e.capacity(),
                    e.slotMinutes(),
                    e.availability(),
                    e.blocksOthers(),
                    partsByUse.getOrDefault(e.getId(), List.of()),
                    periodsByUse.getOrDefault(e.getId(), List.of()),
                    e.weekdays(),
                    e.price(),
                    e.rules(),
                    e.archivedAt(),
                    e.version() == null ? 0 : e.version(),
                    e.createdAt(),
                    e.updatedAt()))
        .toList();
  }

  /**
   * As restricións da base tradúcense ao código do dominio; o resto sae como está. Tamén a
   * restrición diferida das partes, que {@link UsePartNameCheck} fai saltar aquí.
   */
  private static FacilityUse translated(FacilityUse use, Supplier<FacilityUse> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException e) {
      String message = String.valueOf(e.getMostSpecificCause().getMessage());
      if (message.contains(NAME_UNIQUE_INDEX)) {
        throw new DomainException(UseError.NAME_TAKEN, Map.of("name", use.name()));
      }
      if (message.contains(PART_NAME_EXCLUSION)) {
        throw new DomainException(UseError.PART_NAME_TAKEN);
      }
      if (message.contains(PERIOD_EXCLUSION)) {
        throw new DomainException(UseError.PERIOD_OVERLAP);
      }
      throw e;
    }
  }
}
