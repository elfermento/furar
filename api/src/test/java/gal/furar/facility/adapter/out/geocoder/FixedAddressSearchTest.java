// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FixedAddressSearchTest {

  final FixedAddressSearch search = new FixedAddressSearch();

  @Test
  void findsByEveryWordWithoutCaseOrAccentsInsideTheMunicipality() {
    assertThat(search.suggest("praza espana", new SearchArea("Ferrol", "15036")))
        .extracting(AddressSuggestion::label)
        .containsExactly("Praza de España 10, Ferrol");
    assertThat(search.suggest("praza espana", new SearchArea("Narón", "15054"))).isEmpty();
    assertThat(search.suggest("rua", new SearchArea("NARON", null))).hasSize(2);
    // Narón e Ferrol, 3; a comarca de Verín, 7.
    assertThat(search.suggest("rua", SearchArea.ANYWHERE)).hasSize(10);
  }

  @Test
  void theVerinAddressesAreFilteredByTheirIneCodeAndMayHaveNoNumber() {
    assertThat(search.suggest("irmans moreno", new SearchArea("Verín", "32085")))
        .extracting(AddressSuggestion::label)
        .containsExactly("Rúa Irmáns Moreno 13, Verín", "Rúa Irmáns Moreno, Verín");
    assertThat(search.suggest("irmans moreno", new SearchArea("Oímbra", "32053"))).isEmpty();
  }

  @Test
  void resolvesItsOwnIdsAndTheNearestPoint() {
    String id = search.suggest("castela", SearchArea.ANYWHERE).getFirst().id();

    assertThat(search.resolve(id))
        .hasValueSatisfying(m -> assertThat(m.ineCode()).isEqualTo("15054"));
    assertThat(search.resolve("c.other")).isEmpty();
    assertThat(search.reverse(new BigDecimal("43.4858"), new BigDecimal("-8.2295")))
        .hasValueSatisfying(m -> assertThat(m.street()).isEqualTo("Rúa Real"));
    assertThat(search.reverse(new BigDecimal("40.4168"), new BigDecimal("-3.7038"))).isEmpty();
  }
}
