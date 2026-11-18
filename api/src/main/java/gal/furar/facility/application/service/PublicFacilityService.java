// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.in.FacilityGallery;
import gal.furar.facility.application.port.in.ListPublicFacilitiesUseCase;
import gal.furar.facility.application.port.in.PublicFacility;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.PublicUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado público das instalacións (portada da veciñanza). Le por páxinas na orde alfabética do
 * idioma só as publicadas e non de baixa (filtro na base, ADR-0022 puntos 10 e 17), quita as que
 * están en borrador (calculado por lote, punto 3) e engade deportes e portada cunha consulta cada
 * un, nunca unha por instalación.
 */
@Service
public class PublicFacilityService implements ListPublicFacilitiesUseCase {

  /** Tamaño de cada páxina lida do repositorio. */
  static final int PAGE = FacilityService.MAX_PAGE_SIZE;

  private final FacilityRepository repository;
  private final FacilityDrafts drafts;
  private final SportRepository sports;
  private final FacilityGalleries galleries;

  /** Os {@link RequirementProbe} poden faltar: entón o requisito falta sempre e nada se ve. */
  @Autowired
  public PublicFacilityService(
      FacilityRepository repository,
      ObjectProvider<RequirementProbe> probes,
      SportRepository sports,
      FacilityGalleries galleries) {
    this(repository, probes.orderedStream().toList(), sports, galleries);
  }

  public PublicFacilityService(
      FacilityRepository repository,
      List<RequirementProbe> probes,
      SportRepository sports,
      FacilityGalleries galleries) {
    this.repository = repository;
    this.drafts = new FacilityDrafts(probes);
    this.sports = sports;
    this.galleries = galleries;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public List<PublicFacility> listPublic(Language language) {
    // Publicadas e non de baixa, filtradas na base (ADR-0022, punto 17); o borrador, por lote.
    List<Facility> candidates = new ArrayList<>();
    for (int page = 0; ; page++) {
      List<Facility> items = repository.findPublishedPage(page, PAGE, language, false);
      candidates.addAll(items);
      if (items.size() < PAGE) {
        break;
      }
    }
    Map<UUID, Set<Requirement>> missing =
        drafts.missing(candidates.stream().map(f -> f.id().value()).toList());
    List<Facility> visible =
        candidates.stream().filter(f -> missing.get(f.id().value()).isEmpty()).toList();
    if (visible.isEmpty()) {
      return List.of();
    }
    Map<SportRef, Sport> named = SportNames.of(sports);
    Map<FacilityId, FacilityGallery> covers = galleries.of(visible);
    return visible.stream()
        .map(f -> new PublicFacility(f, SportNames.resolve(named, f), covers.get(f.id()).cover()))
        .toList();
  }
}
