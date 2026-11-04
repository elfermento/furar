// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UseError;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePart;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Porto de saída dos usos en memoria, por concello coma o real: nome único entre os vivos da
 * instalación e nomes de partes únicos no uso (o que na base son índices).
 */
public class InMemoryFacilityUseRepository implements FacilityUseRepository {

  private final Map<UUID, Map<UseId, FacilityUse>> byMunicipality = new LinkedHashMap<>();

  /** Rexistro de operacións, para comprobar a orde co bloqueo. */
  public final List<String> calls;

  public InMemoryFacilityUseRepository(List<String> calls) {
    this.calls = calls;
  }

  private Map<UseId, FacilityUse> current() {
    return byMunicipality.computeIfAbsent(TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public List<FacilityUse> findActive(FacilityId facilityId) {
    return current().values().stream()
        .filter(u -> u.facilityId().equals(facilityId) && !u.isArchived())
        .toList();
  }

  @Override
  public List<FacilityUse> findAll(FacilityId facilityId) {
    return current().values().stream().filter(u -> u.facilityId().equals(facilityId)).toList();
  }

  @Override
  public List<FacilityUse> findAllInMunicipality() {
    return List.copyOf(current().values());
  }

  /** As partes arquivadas gardadas por {@link #insertArchivedParts}. */
  public final List<ArchivedUsePart> archivedParts = new ArrayList<>();

  @Override
  public List<ArchivedUsePart> findArchivedParts(FacilityId facilityId) {
    return archivedParts.stream().filter(p -> findById(facilityId, p.useId()).isPresent()).toList();
  }

  @Override
  public void insertArchivedParts(List<ArchivedUsePart> parts) {
    archivedParts.addAll(parts);
  }

  @Override
  public Optional<FacilityUse> findById(FacilityId facilityId, UseId useId) {
    return Optional.ofNullable(current().get(useId)).filter(u -> u.facilityId().equals(facilityId));
  }

  @Override
  public Optional<FacilityUse> findById(UseId useId) {
    return Optional.ofNullable(current().get(useId));
  }

  @Override
  public List<FacilityUse> findAllById(Collection<UseId> ids) {
    return ids.stream().map(current()::get).filter(Objects::nonNull).toList();
  }

  @Override
  public FacilityUse insert(FacilityUse use) {
    calls.add("insert-use");
    check(use);
    return store(use, 0);
  }

  @Override
  public FacilityUse update(FacilityUse use) {
    calls.add("update-use");
    FacilityUse stored = current().get(use.id());
    if (stored == null) {
      throw new DomainException(UseError.NOT_FOUND);
    }
    if (!Objects.equals(stored.version(), use.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    check(use);
    return store(use, use.version() + 1);
  }

  private void check(FacilityUse use) {
    if (use.isArchived()) {
      return;
    }
    String name = use.name().toLowerCase(Locale.ROOT);
    boolean taken =
        findActive(use.facilityId()).stream()
            .anyMatch(
                u -> !u.id().equals(use.id()) && u.name().toLowerCase(Locale.ROOT).equals(name));
    if (taken) {
      throw new DomainException(UseError.NAME_TAKEN);
    }
    Set<String> parts = new HashSet<>();
    for (UsePart part : use.parts()) {
      if (!parts.add(part.name().toLowerCase(Locale.ROOT))) {
        throw new DomainException(UseError.PART_NAME_TAKEN);
      }
    }
  }

  private FacilityUse store(FacilityUse u, long version) {
    FacilityUse saved =
        FacilityUse.rehydrate(
            u.id(),
            u.facilityId(),
            u.name(),
            u.rentalMode(),
            u.capacity(),
            u.slotMinutes(),
            u.availability(),
            u.blocksOthers(),
            new ArrayList<>(u.parts()),
            u.periods(),
            u.weekdays(),
            u.price(),
            u.rules(),
            u.archivedAt(),
            version,
            u.createdAt(),
            u.updatedAt());
    current().put(saved.id(), saved);
    return saved;
  }
}
