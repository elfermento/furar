// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
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
 * Adaptador JPA, sempre dentro dun concello (A7b): sen concello falla antes de consultar. A
 * instalación carga cos seus deportes e servizos nunha consulta por táboa e lote; ao gardar,
 * substitúe as filas de deportes e servizos (DELETE + {@code flush} antes dos INSERT: Hibernate
 * executa os INSERT primeiro, ADR-0022 punto 15).
 */
@Component
class FacilityPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements FacilityRepository, FacilityLock {

  /** Restricións que son erros do xestor (migracións {@code facility_create} e {@code _data}). */
  static final Map<String, FacilityError> CONSTRAINTS =
      Map.of(
          "facility_name_uk", FacilityError.NAME_TAKEN,
          "facility_sport_code_uk", FacilityError.SPORT_DUPLICATED,
          "facility_sport_custom_uk", FacilityError.SPORT_DUPLICATED,
          "facility_sport_catalog_fk", FacilityError.SPORT_UNKNOWN,
          "facility_sport_custom_fk", FacilityError.SPORT_UNKNOWN);

  private final FacilityJpaRepository jpa;
  private final FacilitySportJpaRepository sports;
  private final FacilityAmenityJpaRepository amenities;
  private final FacilitySql sql;

  FacilityPersistenceAdapter(
      FacilityJpaRepository jpa,
      FacilitySportJpaRepository sports,
      FacilityAmenityJpaRepository amenities,
      FacilitySql sql) {
    this.jpa = jpa;
    this.sports = sports;
    this.amenities = amenities;
    this.sql = sql;
  }

  @Override
  public Optional<Facility> findById(FacilityId id) {
    tenant();
    return jpa.findById(id.value()).map(e -> toDomain(List.of(e)).getFirst());
  }

  @Override
  public List<Facility> findAllById(List<FacilityId> ids) {
    tenant();
    Map<UUID, Facility> found =
        toDomain(jpa.findAllById(ids.stream().map(FacilityId::value).toList())).stream()
            .collect(Collectors.toMap(f -> f.id().value(), f -> f));
    return ids.stream().map(id -> found.get(id.value())).filter(Objects::nonNull).toList();
  }

  @Override
  public PageResult<Facility> findPage(int page, int size, Language language) {
    UUID municipality = tenant().id();
    List<UUID> ids = sql.pageIds(municipality, language, size, (long) page * size);
    List<Facility> items = findAllById(ids.stream().map(FacilityId::new).toList());
    return new PageResult<>(items, page, size, jpa.count());
  }

  @Override
  public List<Facility> findPublishedPage(
      int page, int size, Language language, boolean onlineBookingOnly) {
    UUID municipality = tenant().id();
    List<UUID> ids =
        sql.publishedPageIds(municipality, language, onlineBookingOnly, size, (long) page * size);
    return findAllById(ids.stream().map(FacilityId::new).toList());
  }

  @Override
  public List<Facility> findAll() {
    tenant();
    return toDomain(jpa.findAll(Sort.by("createdAt", "id")));
  }

  @Override
  public Facility insert(Facility facility) {
    tenant();
    return translated(
        facility,
        () -> {
          FacilityEntity saved = jpa.saveAndFlush(FacilityPersistenceMapper.newEntity(facility));
          writeChildren(facility);
          return reload(saved);
        });
  }

  @Override
  public Facility update(Facility facility) {
    tenant();
    FacilityEntity entity =
        jpa.findById(facility.id().value())
            .orElseThrow(() -> new DomainException(FacilityError.NOT_FOUND));
    if (!Objects.equals(entity.version(), facility.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    FacilityPersistenceMapper.apply(entity, facility);
    return translated(
        facility,
        () -> {
          FacilityEntity saved = jpa.saveAndFlush(entity);
          List<UUID> id = List.of(facility.id().value());
          sports.deleteAll(sports.findByFacilityIdInOrderByPositionAsc(id));
          sports.flush();
          amenities.deleteAll(amenities.findByFacilityIdIn(id));
          amenities.flush();
          writeChildren(facility);
          return reload(saved);
        });
  }

  @Override
  public boolean lock(FacilityId id) {
    return sql.lock(tenant().id(), id.value());
  }

  private void writeChildren(Facility facility) {
    sports.saveAllAndFlush(FacilityPersistenceMapper.sports(facility));
    amenities.saveAllAndFlush(FacilityPersistenceMapper.amenities(facility));
  }

  private Facility reload(FacilityEntity saved) {
    return toDomain(List.of(saved)).getFirst();
  }

  /** Cada instalación cos seus deportes e servizos: unha consulta por táboa para todo o lote. */
  private List<Facility> toDomain(Collection<FacilityEntity> entities) {
    if (entities.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = entities.stream().map(FacilityEntity::getId).toList();
    Map<UUID, List<FacilitySportEntity>> sportsOf = new HashMap<>();
    sports
        .findByFacilityIdInOrderByPositionAsc(ids)
        .forEach(s -> sportsOf.computeIfAbsent(s.facilityId(), k -> new ArrayList<>()).add(s));
    Map<UUID, List<FacilityAmenityEntity>> amenitiesOf =
        amenities.findByFacilityIdIn(ids).stream()
            .collect(Collectors.groupingBy(FacilityAmenityEntity::facilityId));
    return entities.stream()
        .map(
            e ->
                FacilityPersistenceMapper.toDomain(
                    e,
                    sportsOf.getOrDefault(e.getId(), List.of()),
                    amenitiesOf.getOrDefault(e.getId(), List.of())))
        .toList();
  }

  /** As restricións de {@link #CONSTRAINTS} tradúcense ao seu erro; o resto sae como está. */
  private static Facility translated(Facility facility, Supplier<Facility> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException e) {
      String message = String.valueOf(e.getMostSpecificCause().getMessage());
      for (Map.Entry<String, FacilityError> entry : CONSTRAINTS.entrySet()) {
        if (message.contains("\"" + entry.getKey() + "\"")) {
          Map<String, Object> params =
              entry.getValue() == FacilityError.NAME_TAKEN
                  ? Map.of("name", facility.name())
                  : Map.of();
          throw new DomainException(entry.getValue(), params);
        }
      }
      throw e;
    }
  }
}
