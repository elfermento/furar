// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRemoval;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenantContext;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Porto de saída en memoria, separado por concello coma o real: nome único sen maiúsculas, orde
 * alfabética do idioma e bloqueo (que só rexistra a orde das chamadas).
 */
public class InMemoryFacilityRepository
    implements FacilityRepository, FacilityLock, FacilityRemoval {

  private final Map<UUID, Map<FacilityId, Facility>> byMunicipality = new LinkedHashMap<>();

  /** Rexistro de operacións («lock», «update»…), para comprobar que o bloqueo vai primeiro. */
  public final List<String> calls = new ArrayList<>();

  private Map<FacilityId, Facility> current() {
    return byMunicipality.computeIfAbsent(TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public Optional<Facility> findById(FacilityId id) {
    return Optional.ofNullable(current().get(id));
  }

  @Override
  public List<Facility> findAllById(List<FacilityId> ids) {
    return ids.stream().map(current()::get).filter(Objects::nonNull).toList();
  }

  @Override
  public PageResult<Facility> findPage(int page, int size, Language language) {
    Collator collator = Collator.getInstance(Locale.of(language.code()));
    List<Facility> all = new ArrayList<>(current().values());
    all.sort(Comparator.comparing(Facility::name, collator));
    int from = Math.min(page * size, all.size());
    return new PageResult<>(
        all.subList(from, Math.min(from + size, all.size())), page, size, all.size());
  }

  @Override
  public List<Facility> findPublishedPage(
      int page, int size, Language language, boolean onlineBookingOnly) {
    Collator collator = Collator.getInstance(Locale.of(language.code()));
    List<Facility> all =
        new ArrayList<>(
            current().values().stream()
                .filter(f -> f.status() != FacilityStatus.WITHDRAWN)
                .filter(Facility::published)
                .filter(f -> !onlineBookingOnly || f.details().onlineBooking())
                .toList());
    all.sort(Comparator.comparing(Facility::name, collator));
    int from = Math.min(page * size, all.size());
    return List.copyOf(all.subList(from, Math.min(from + size, all.size())));
  }

  @Override
  public List<Facility> findAll() {
    return List.copyOf(current().values());
  }

  @Override
  public Facility insert(Facility facility) {
    calls.add("insert");
    checkName(facility);
    return store(facility, 0);
  }

  @Override
  public Facility update(Facility facility) {
    calls.add("update");
    Facility stored = current().get(facility.id());
    if (stored == null) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
    if (!Objects.equals(stored.version(), facility.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    checkName(facility);
    return store(facility, facility.version() + 1);
  }

  @Override
  public boolean lock(FacilityId id) {
    calls.add("lock");
    return current().containsKey(id);
  }

  private void checkName(Facility facility) {
    String name = facility.name().toLowerCase(Locale.ROOT);
    boolean taken =
        current().values().stream()
            .anyMatch(
                f ->
                    !f.id().equals(facility.id())
                        && f.name().toLowerCase(Locale.ROOT).equals(name));
    if (taken) {
      throw new DomainException(FacilityError.NAME_TAKEN);
    }
  }

  private Facility store(Facility facility, long version) {
    Facility saved =
        Facility.rehydrate(
            facility.id(),
            facility.name(),
            facility.status(),
            facility.address(),
            facility.published(),
            facility.details(),
            facility.description(),
            facility.houseRules(),
            facility.paymentNotes(),
            facility.amenities(),
            version,
            facility.createdAt(),
            facility.updatedAt());
    current().put(saved.id(), saved);
    return saved;
  }

  @Override
  public void remove(FacilityId id) {
    calls.add("remove");
    current().remove(id);
  }
}
