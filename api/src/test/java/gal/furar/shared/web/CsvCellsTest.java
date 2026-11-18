// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvCellsTest {

  @Test
  void textNeutralisesFormulasAndQuotes() {
    assertThat(CsvCells.text("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    assertThat(CsvCells.text("a;b")).isEqualTo("\"a;b\"");
    assertThat(CsvCells.text("o \"alto\"\n")).isEqualTo("\"o \"\"alto\"\"\n\"");
    assertThat(CsvCells.text(null)).isEmpty();
  }

  @Test
  void decimalsWithPointAndScale() {
    assertThat(CsvCells.decimal(new BigDecimal("43.5"), 6)).isEqualTo("43.500000");
    assertThat(CsvCells.decimal(new BigDecimal("1234.5"), 2)).isEqualTo("1234.50");
    assertThat(CsvCells.decimal(null, 2)).isEmpty();
  }

  @Test
  void listsWithPipeAndEscapes() {
    assertThat(CsvCells.list(List.of("padel", "tenis"))).isEqualTo("padel|tenis");
    assertThat(CsvCells.list(List.of("a|b", "c\\d"))).isEqualTo("a\\|b|c\\\\d");
    assertThat(CsvCells.list(List.of())).isEmpty();
  }
}
