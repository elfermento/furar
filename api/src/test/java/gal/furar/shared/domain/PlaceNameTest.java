// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlaceNameTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "A Coruña|da Coruña|de A Coruña",
        "O Porriño|do Porriño|de O Porriño",
        "As Pontes de García Rodríguez|das Pontes de García Rodríguez|de As Pontes de García Rodríguez",
        "Os Blancos|dos Blancos|de Os Blancos",
        "Narón|de Narón|de Narón",
        "Oleiros|de Oleiros|de Oleiros",
        "Ames|de Ames|de Ames",
        "  Ourense |de Ourense|de Ourense",
        // A denominación en vez do topónimo: sen «Concello de Concello de…».
        "Concello de Proba|de Proba|de Proba",
        "concello  da Coruña|da Coruña|de A Coruña",
        "Ayuntamiento de O Porriño|do Porriño|de O Porriño",
        "Concello dos Blancos|dos Blancos|de Os Blancos",
        "Concello|de Concello|de Concello",
      })
  void galicianContractsTheArticleAndSpanishDoesNot(String name, String gl, String es) {
    assertThat(PlaceName.of(name, Language.GL)).isEqualTo(gl);
    assertThat(PlaceName.of(name, Language.ES)).isEqualTo(es);
  }
}
