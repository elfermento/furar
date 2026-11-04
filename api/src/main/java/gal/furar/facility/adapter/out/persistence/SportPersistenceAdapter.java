// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.domain.CustomSport;
import gal.furar.facility.domain.CustomSportId;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Deportes: o catálogo da plataforma (só lectura, igual para todos) e os do concello actual. Tamén
 * di que instalacións cumpren o requisito {@code SPORTS} do borrador (ADR-0022, punto 3), cunha
 * consulta por lote.
 */
@Component
class SportPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements SportRepository, RequirementProbe {

  /** Índice único do nome do deporte do concello (migración {@code facility_data}). */
  static final String LABEL_UNIQUE_INDEX = "custom_sport_label_uk";

  private final SportCatalogJpaRepository catalog;
  private final CustomSportJpaRepository custom;
  private final FacilitySportJpaRepository facilitySports;

  SportPersistenceAdapter(
      SportCatalogJpaRepository catalog,
      CustomSportJpaRepository custom,
      FacilitySportJpaRepository facilitySports) {
    this.catalog = catalog;
    this.custom = custom;
    this.facilitySports = facilitySports;
  }

  @Override
  public List<Sport> findAll() {
    tenant();
    List<Sport> all = new ArrayList<>();
    catalog.findAll().forEach(c -> all.add(catalogSport(c)));
    custom.findAll().forEach(c -> all.add(customSport(c).sport()));
    return all;
  }

  @Override
  public List<Sport> findAll(Collection<SportRef> refs) {
    tenant();
    if (refs.isEmpty()) {
      return List.of();
    }
    Set<String> codes = new HashSet<>();
    Set<UUID> ids = new HashSet<>();
    for (SportRef ref : refs) {
      if (ref.isCustom()) {
        ids.add(Objects.requireNonNull(ref.customSportId()).value());
      } else {
        codes.add(ref.code());
      }
    }
    Map<SportRef, Sport> found = new HashMap<>();
    catalog.findAllById(codes).forEach(c -> found.put(SportRef.catalog(c.code()), catalogSport(c)));
    custom
        .findAllById(ids)
        .forEach(c -> found.put(SportRef.custom(c.getId()), customSport(c).sport()));
    return refs.stream().map(found::get).filter(Objects::nonNull).toList();
  }

  @Override
  public List<CustomSport> findAllCustom() {
    tenant();
    return custom.findAll(Sort.by("createdAt", "id")).stream()
        .map(SportPersistenceAdapter::customSport)
        .toList();
  }

  @Override
  public CustomSport insert(CustomSport sport) {
    tenant();
    try {
      return customSport(
          custom.saveAndFlush(
              new CustomSportEntity(
                  sport.id().value(),
                  FacilityPersistenceMapper.text(sport.label().gl(), sport.label().es()),
                  sport.createdAt())));
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(LABEL_UNIQUE_INDEX)) {
        throw new DomainException(
            FacilityError.SPORT_NAME_TAKEN, Map.of("name", sport.label().gl()));
      }
      throw e;
    }
  }

  @Override
  public Requirement requirement() {
    return Requirement.SPORTS;
  }

  @Override
  public Set<UUID> satisfiedBy(Collection<UUID> facilityIds) {
    tenant();
    return facilityIds.isEmpty()
        ? Set.of()
        : Set.copyOf(facilitySports.facilitiesWithSports(facilityIds));
  }

  private static Sport catalogSport(SportCatalogEntity c) {
    return new Sport(SportRef.catalog(c.code()), FacilityPersistenceMapper.text(c.label()));
  }

  private static CustomSport customSport(CustomSportEntity c) {
    return new CustomSport(
        new CustomSportId(c.getId()), FacilityPersistenceMapper.text(c.label()), c.createdAt());
  }
}
