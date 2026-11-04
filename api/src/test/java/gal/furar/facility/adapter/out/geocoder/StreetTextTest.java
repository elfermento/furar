// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StreetTextTest {

  @Test
  void upperCaseWordsGetAnInitialCapitalAndParticlesStayLowerCase() {
    assertThat(StreetText.pretty("RUA CONCEPCION ARENAL 100, Narón"))
        .isEqualTo("Rua Concepcion Arenal 100, Narón");
    assertThat(StreetText.pretty("PLAZA DE ESPAÑA 10, Ferrol"))
        .isEqualTo("Plaza de España 10, Ferrol");
    assertThat(StreetText.pretty("RUA REAL ALTA 10, A Graña (Ferrol)"))
        .isEqualTo("Rua Real Alta 10, A Graña (Ferrol)");
    assertThat(StreetText.pretty("A  CORUÑA")).isEqualTo("A Coruña");
    assertThat(StreetText.pretty("ESTRADA DA GANDARA")).isEqualTo("Estrada da Gandara");
  }
}
