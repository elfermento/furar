// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.facility.application.port.in.FacilityCatalog;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.in.PublicFacility;
import gal.furar.facility.application.service.FacilityCatalogService;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.ImageRef;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Portada común (ADR-0030): cada concello lese dentro do seu, e só sae se ten algunha instalación
 * activa á vista.
 */
class FacilityCatalogServiceTest {

  static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

  final CatalogMunicipalities.Listed naron =
      new CatalogMunicipalities.Listed(new TenantRef(UUID.randomUUID(), "naron"), "Narón");
  final CatalogMunicipalities.Listed cambre =
      new CatalogMunicipalities.Listed(new TenantRef(UUID.randomUUID(), "cambre"), "Cambre");
  final CatalogMunicipalities.Listed ordes =
      new CatalogMunicipalities.Listed(new TenantRef(UUID.randomUUID(), "ordes"), "Ordes");

  /** O que ve a veciñanza de cada concello. */
  final Map<UUID, List<PublicFacility>> visible = new HashMap<>();

  final List<TenantRef> visited = new ArrayList<>();
  boolean hasNext;

  final CatalogMunicipalities municipalities =
      (page, size) -> new CatalogMunicipalities.Page(List.of(cambre, naron, ordes), hasNext);

  final FacilityCatalogService service =
      new FacilityCatalogService(
          municipalities,
          language -> {
            TenantRef current = TenantContext.require();
            visited.add(current);
            return visible.getOrDefault(current.id(), List.of());
          });

  static PublicFacility facility(String name, FacilityStatus status) {
    Facility created =
        Facility.create(
            FacilityId.newId(),
            name,
            new Address(
                "Rúa Buxo", null, "15570", "Narón", new BigDecimal("43.5"), new BigDecimal("-8.2")),
            NOW);
    if (status != FacilityStatus.ACTIVE) {
      created = created.changeStatus(status, NOW);
    }
    return new PublicFacility(
        created,
        List.of(),
        new ImageView(
            new ImageRef.OfDrawing("sports_hall"),
            new LocalizedText("Pavillón", "Pabellón"),
            1600,
            900,
            null,
            null));
  }

  @Test
  void eachMunicipalityIsReadInsideItselfAndOnlyTheOpenOnesAreListed() {
    PublicFacility hall = facility("Pavillón", FacilityStatus.ACTIVE);
    PublicFacility pool = facility("Piscina", FacilityStatus.TEMPORARILY_CLOSED);
    visible.put(naron.tenant().id(), List.of(hall, pool));
    visible.put(
        cambre.tenant().id(), List.of(facility("Pista", FacilityStatus.TEMPORARILY_CLOSED)));
    hasNext = true;

    FacilityCatalog catalog = service.catalog(Language.GL, 0, 20);

    assertThat(visited)
        .as("one read per municipality, each with its own id and slug")
        .containsExactly(cambre.tenant(), naron.tenant(), ordes.tenant());
    assertThat(TenantContext.current()).as("the thread is left without municipality").isEmpty();
    assertThat(catalog.items())
        .as("only temporarily closed, or nothing to see: not listed")
        .extracting(FacilityCatalog.Entry::municipality)
        .containsExactly(naron.tenant());
    assertThat(catalog.items().get(0).facilities())
        .as("a listed municipality shows what its own list shows")
        .containsExactly(hall, pool);
    assertThat(catalog.items().get(0).name()).isEqualTo("Narón");
    assertThat(catalog.hasNext()).isTrue();
  }

  @Test
  void aPageOrSizeOutOfRangeIsAnInvalidParameter() {
    for (int[] bad : new int[][] {{-1, 20}, {0, 0}, {0, 51}}) {
      DomainException e =
          catchThrowableOfType(
              DomainException.class, () -> service.catalog(Language.GL, bad[0], bad[1]));
      assertThat(e.errorCode().code()).isEqualTo("common.invalid-parameter");
    }
    assertThat(visited).isEmpty();
  }
}
