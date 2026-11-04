// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.domain.CustomSport;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Deportes en memoria: un catálogo pequeno igual para todos e os do concello separados coma na
 * base, co nome único sen maiúsculas.
 */
public class InMemorySportRepository implements SportRepository {

  public static final List<Sport> CATALOG =
      List.of(
          new Sport(SportRef.catalog("football"), new LocalizedText("Fútbol", "Fútbol")),
          new Sport(SportRef.catalog("handball"), new LocalizedText("Balonmán", "Balonmano")),
          new Sport(SportRef.catalog("padel"), new LocalizedText("Pádel", "Pádel")));

  private final Map<UUID, List<CustomSport>> byMunicipality = new LinkedHashMap<>();

  private List<CustomSport> current() {
    return byMunicipality.computeIfAbsent(TenantContext.require().id(), k -> new ArrayList<>());
  }

  @Override
  public List<Sport> findAll() {
    List<Sport> all = new ArrayList<>(CATALOG);
    current().forEach(c -> all.add(c.sport()));
    return all;
  }

  @Override
  public List<Sport> findAll(Collection<SportRef> refs) {
    List<Sport> all = findAll();
    return refs.stream()
        .map(ref -> all.stream().filter(s -> s.ref().equals(ref)).findFirst().orElse(null))
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public List<CustomSport> findAllCustom() {
    return List.copyOf(current());
  }

  @Override
  public CustomSport insert(CustomSport sport) {
    String gl = sport.label().gl().toLowerCase(Locale.ROOT);
    if (current().stream().anyMatch(c -> c.label().gl().toLowerCase(Locale.ROOT).equals(gl))) {
      throw new DomainException(FacilityError.SPORT_NAME_TAKEN);
    }
    current().add(sport);
    return sport;
  }
}
