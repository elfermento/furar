// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchAreaTest {

  @Test
  void theDemoQualifierIsNotPartOfThePlace() {
    SearchArea demo = new SearchArea("Narón (demo)");
    assertThat(demo.municipalityName()).isEqualTo("Narón");
    assertThat(demo.contains("NARON")).isTrue();
    assertThat(new SearchArea("Castrelo do Val (demo)").contains("Castrelo do Val")).isTrue();
    assertThat(new SearchArea("Narón").contains("Ferrol")).isFalse();
    // Só entre parénteses ao final; un nome que fose só iso queda como está.
    assertThat(new SearchArea("(demo)").municipalityName()).isEqualTo("(demo)");
    assertThat(SearchArea.ANYWHERE.contains("Calquera")).isTrue();
  }
}
