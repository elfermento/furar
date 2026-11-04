// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.application.port.out.AddressSearch;
import gal.furar.facility.application.service.AddressSearchService;
import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSearchError;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressSearchServiceTest {

  final UUID municipality = UUID.randomUUID();
  final List<String> queries = new ArrayList<>();
  final List<SearchArea> areas = new ArrayList<>();
  List<AddressSuggestion> answer = List.of();
  Optional<MunicipalityPlaces.Place> place =
      Optional.of(new MunicipalityPlaces.Place("Narón", "15054"));

  final AddressSearch port =
      new AddressSearch() {
        @Override
        public List<AddressSuggestion> suggest(String query, SearchArea area) {
          queries.add(query);
          areas.add(area);
          return answer;
        }

        @Override
        public Optional<AddressMatch> resolve(String id) {
          return Optional.empty();
        }

        @Override
        public Optional<AddressMatch> reverse(BigDecimal latitude, BigDecimal longitude) {
          return Optional.of(
              new AddressMatch("Rúa Buxo", "16", "15572", "Narón", "15054", latitude, longitude));
        }
      };

  final AddressSearchService service =
      new AddressSearchService(port, id -> id.equals(municipality) ? place : Optional.empty());
  TenantContext.Scope scope;

  @BeforeEach
  void tenant() {
    scope = TenantContext.open(new TenantRef(municipality, "naron"));
  }

  @AfterEach
  void close() {
    scope.close();
  }

  @Test
  void lessThanThreeOrMoreThanAHundredCharactersDoNotGoOut() {
    assertThat(service.suggest("ru")).isEmpty();
    assertThat(service.suggest("  ru  ")).isEmpty();
    assertThat(service.suggest("")).isEmpty();
    assertThat(service.suggest(null)).isEmpty();
    assertThat(service.suggest("x".repeat(101))).isEmpty();
    assertThat(queries).isEmpty();

    service.suggest("rúa");
    service.suggest("x".repeat(100));
    assertThat(queries).hasSize(2);
  }

  @Test
  void theQueryIsNormalizedAndSearchedInTheCurrentMunicipality() {
    service.suggest("  rúa   real  ");

    assertThat(queries).containsExactly("rúa real");
    assertThat(areas).containsExactly(new SearchArea("Narón", "15054"));
  }

  @Test
  void aMunicipalityThatIsNotFoundSearchesAnywhere() {
    place = Optional.empty();

    service.suggest("rúa real");

    assertThat(areas).containsExactly(SearchArea.ANYWHERE);
  }

  @Test
  void atMostTenSuggestions() {
    answer =
        IntStream.range(0, 12).mapToObj(i -> new AddressSuggestion("f." + i, "Rúa " + i)).toList();

    assertThat(service.suggest("rúa")).hasSize(10);
  }

  @Test
  void anUnknownSuggestionIsNotFound() {
    assertThatThrownBy(() -> service.resolve("f.nope"))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(AddressSearchError.ADDRESS_NOT_FOUND));
  }

  @Test
  void reverseChecksTheCoordinates() {
    assertThat(service.reverse(new BigDecimal("43.51"), new BigDecimal("-8.19")).street())
        .isEqualTo("Rúa Buxo");
    assertThatThrownBy(() -> service.reverse(new BigDecimal("91"), new BigDecimal("-181")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(CommonError.VALIDATION_FAILED);
              assertThat(e.violations()).extracting(v -> v.field()).containsExactly("lat", "lon");
            });
  }
}
