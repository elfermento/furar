// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.application.port.in.PublicFacility;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.service.PublicFacilityService;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.DrawingSuggestion;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDetails;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Listado público: só o publicado (nin borrador nin de baixa), en orde, con deportes e portada. */
class PublicFacilityServiceTest {

  static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

  final InMemoryFacilityRepository repository = new InMemoryFacilityRepository();
  final InMemorySportRepository sports = new InMemorySportRepository();
  final InMemoryImages images = new InMemoryImages();

  /** Instalacións que cumpren USES e SCHEDULE (SPORTS calcúlase cos deportes gardados). */
  final Set<UUID> complete = new HashSet<>();

  final PublicFacilityService service =
      new PublicFacilityService(
          repository,
          List.of(
              probe(Requirement.USES),
              probe(Requirement.SCHEDULE),
              new RequirementProbe() {
                @Override
                public Requirement requirement() {
                  return Requirement.SPORTS;
                }

                @Override
                public Set<UUID> satisfiedBy(Collection<UUID> ids) {
                  return ids.stream()
                      .filter(
                          id ->
                              !repository
                                  .findById(new FacilityId(id))
                                  .orElseThrow()
                                  .details()
                                  .sports()
                                  .isEmpty())
                      .collect(Collectors.toSet());
                }
              }),
          sports,
          images.galleries);

  TenantContext.Scope scope;

  @BeforeEach
  void enter() {
    scope = TenantContext.open(new TenantRef(UUID.randomUUID(), "naron"));
  }

  @AfterEach
  void leave() {
    scope.close();
  }

  RequirementProbe probe(Requirement requirement) {
    return new RequirementProbe() {
      @Override
      public Requirement requirement() {
        return requirement;
      }

      @Override
      public Set<UUID> satisfiedBy(Collection<UUID> ids) {
        return ids.stream().filter(complete::contains).collect(Collectors.toSet());
      }
    };
  }

  static Address address() {
    return new Address(
        "Rúa Buxo",
        null,
        "15570",
        "Narón",
        "15054",
        new BigDecimal("43.5"),
        new BigDecimal("-8.2"));
  }

  Facility facility(String name, FacilityStatus status, boolean published, SportRef... refs) {
    return facility(name, status, published, true, refs);
  }

  /** {@code complete}: non lle falta nada; {@code published}: pasou polo «Publicar». */
  Facility facility(
      String name, FacilityStatus status, boolean complete, boolean published, SportRef... refs) {
    Facility created = Facility.create(FacilityId.newId(), name, address(), NOW);
    if (published) {
      created = created.publish(NOW);
    }
    Facility saved = repository.insert(created);
    if (refs.length > 0) {
      saved =
          repository.update(
              saved.updateDetails(
                  new FacilityDetails(List.of(refs), null, null, null, null, true, false, null),
                  NOW));
    }
    if (status != FacilityStatus.ACTIVE) {
      saved = repository.update(saved.changeStatus(status, NOW));
    }
    if (complete) {
      this.complete.add(saved.id().value());
    }
    return saved;
  }

  static Photo photo(String alt) {
    return new Photo(
        PhotoId.newId(),
        new LocalizedText(alt, alt + " (es)"),
        1600,
        1067,
        "image/jpeg",
        NOW,
        null);
  }

  @Test
  void onlyPublishedFacilitiesInAlphabeticalOrderWithSportsAndCover() {
    SportRef padel = SportRef.catalog("padel");
    SportRef football = SportRef.catalog("football");
    Facility pavilion = facility("Pavillón de Piñeiros", FacilityStatus.ACTIVE, true, football);
    Facility court = facility("Pista de pádel 1", FacilityStatus.ACTIVE, true, padel, football);
    Facility closed =
        facility("Campo de fútbol 1", FacilityStatus.TEMPORARILY_CLOSED, true, football);
    facility("Ximnasio en borrador", FacilityStatus.ACTIVE, false, football);
    facility("Pista completa sen publicar", FacilityStatus.ACTIVE, true, false, football);
    facility("Pista sen deportes", FacilityStatus.ACTIVE, true);
    facility("Piscina de baixa", FacilityStatus.WITHDRAWN, true, football);
    Photo first = photo("Pista de pádel");
    Photo second = photo("Rede");
    images.photos.restore(first);
    images.photos.restore(second);
    images.images.replace(
        court.id(),
        new FacilityImages(
            List.of(
                ImageRef.photo(second.id()),
                ImageRef.photo(first.id()),
                ImageRef.drawing(DrawingSuggestion.PADEL_COURT))));

    List<PublicFacility> visible = service.listPublic(Language.GL);

    assertThat(visible)
        .extracting(f -> f.value().name())
        .containsExactly("Campo de fútbol 1", "Pavillón de Piñeiros", "Pista de pádel 1");
    PublicFacility padelCourt = visible.get(2);
    assertThat(padelCourt.sports())
        .extracting(s -> s.label().gl())
        .containsExactly("Pádel", "Fútbol");
    assertThat(padelCourt.cover().ref()).isEqualTo(ImageRef.photo(second.id()));
    assertThat(padelCourt.cover().alt().gl()).isEqualTo("Rede");
    assertThat(visible.get(0).value().id()).isEqualTo(closed.id());
    assertThat(visible.get(0).cover().ref())
        .as("sen imaxes, o debuxo suxerido polo nome")
        .isEqualTo(ImageRef.drawing(DrawingSuggestion.FOOTBALL_FIELD));
    assertThat(visible.get(1).cover().ref())
        .isEqualTo(ImageRef.drawing(DrawingSuggestion.SPORTS_HALL));
    assertThat(visible.get(1).value().id()).isEqualTo(pavilion.id());
  }

  @Test
  void withoutProbesNothingIsPublishedAndEmptyMunicipalitiesAnswerEmpty() {
    assertThat(service.listPublic(Language.ES)).isEmpty();
    facility("Pavillón", FacilityStatus.ACTIVE, true, SportRef.catalog("football"));
    PublicFacilityService noProbes =
        new PublicFacilityService(repository, List.of(), sports, images.galleries);
    assertThat(noProbes.listPublic(Language.GL)).isEmpty();
  }

  @Test
  void readsEveryPageOfTheRepository() {
    for (int i = 0; i < 105; i++) {
      facility("Pista " + (1000 + i), FacilityStatus.ACTIVE, true, SportRef.catalog("padel"));
    }
    List<PublicFacility> visible = service.listPublic(Language.GL);
    assertThat(visible).hasSize(105);
    assertThat(visible.getLast().value().name()).isEqualTo("Pista 1104");
  }
}
