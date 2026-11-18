// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.booking.application.port.in.CatalogAvailability;
import gal.furar.booking.application.port.in.FacilitiesAvailability;
import gal.furar.booking.application.port.in.ReadAvailabilityUseCase;
import gal.furar.booking.application.port.in.UseAvailability;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Ocos da portada común (ADR-0030): os de cada concello, lidos dentro do seu, un tras outro. */
class CatalogAvailabilityServiceTest {

  static final Instant NOW = Instant.parse("2026-11-10T22:30:00Z");

  final CatalogMunicipalities.Listed naron =
      new CatalogMunicipalities.Listed(new TenantRef(UUID.randomUUID(), "naron"), "Narón");
  final CatalogMunicipalities.Listed cambre =
      new CatalogMunicipalities.Listed(new TenantRef(UUID.randomUUID(), "cambre"), "Cambre");
  final Map<UUID, List<FacilitiesAvailability.Item>> free = new HashMap<>();
  final List<TenantRef> visited = new ArrayList<>();
  final List<LocalDate> days = new ArrayList<>();

  final CatalogMunicipalities municipalities =
      (page, size) -> new CatalogMunicipalities.Page(List.of(cambre, naron), page == 0);

  final ReadAvailabilityUseCase availability =
      new ReadAvailabilityUseCase() {
        @Override
        public UseAvailability ofUse(UUID facilityId, UUID useId, @Nullable LocalDate date) {
          throw new UnsupportedOperationException();
        }

        @Override
        public FacilitiesAvailability ofPage(
            @Nullable LocalDate date, int page, int size, Set<String> sports) {
          throw new UnsupportedOperationException();
        }

        @Override
        public FacilitiesAvailability ofAllFacilities(
            @Nullable LocalDate date, Set<String> sports) {
          TenantRef current = TenantContext.require();
          visited.add(current);
          days.add(date);
          List<FacilitiesAvailability.Item> items = free.getOrDefault(current.id(), List.of());
          return new FacilitiesAvailability(date, date, NOW, 0, items.size(), items);
        }
      };

  final CatalogAvailabilityService service =
      new CatalogAvailabilityService(
          municipalities, availability, Clock.fixed(NOW, ZoneOffset.UTC));

  static FacilitiesAvailability.Item item(int freeCount) {
    return new FacilitiesAvailability.Item(UUID.randomUUID(), freeCount, List.of());
  }

  @Test
  void theSlotsOfEveryListedMunicipalityComeTogetherEachReadInsideItself() {
    FacilitiesAvailability.Item court = item(4);
    FacilitiesAvailability.Item hall = item(0);
    FacilitiesAvailability.Item field = item(2);
    free.put(cambre.tenant().id(), List.of(court));
    free.put(naron.tenant().id(), List.of(hall, field));
    LocalDate day = LocalDate.of(2026, 11, 20);

    CatalogAvailability result = service.ofCatalog(day, 0, 20);

    assertThat(visited).containsExactly(cambre.tenant(), naron.tenant());
    assertThat(TenantContext.current()).isEmpty();
    assertThat(result.items()).containsExactly(court, hall, field);
    assertThat(result.date()).isEqualTo(day);
    assertThat(result.hasNext()).isTrue();
    assertThat(service.ofCatalog(day, 1, 20).hasNext()).isFalse();
  }

  @Test
  void withoutDateItIsTodayInMadrid() {
    CatalogAvailability result = service.ofCatalog(null, 0, 20);

    assertThat(result.date())
        .as("22:30 UTC on the 10th is already the 10th at 23:30 in Madrid")
        .isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(result.today()).isEqualTo(result.date());
    assertThat(days).containsOnly(LocalDate.of(2026, 11, 10));
  }

  @Test
  void aPageOrSizeOutOfRangeIsAnInvalidParameter() {
    for (int[] bad : new int[][] {{-1, 20}, {0, 0}, {0, 51}}) {
      DomainException e =
          catchThrowableOfType(
              DomainException.class, () -> service.ofCatalog(null, bad[0], bad[1]));
      assertThat(e.errorCode().code()).isEqualTo("common.invalid-parameter");
    }
    assertThat(visited).isEmpty();
  }
}
