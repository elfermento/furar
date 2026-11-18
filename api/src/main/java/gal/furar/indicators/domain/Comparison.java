// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Os dous tramos que compara a variación das cifras (ADR-0026, revisión do 2026-10-29). Nun período
 * pechado (ou que aínda non empezou), o período enteiro fronte ao anterior enteiro. No período en
 * curso, «ata hoxe»: do primeiro día a hoxe fronte ao mesmo tramo do anterior (do 1 ao 29 de
 * outubro fronte ao 1 ao 29 de setembro). Datas de Europe/Madrid cos dous extremos incluídos.
 *
 * @param from primeiro día do tramo actual
 * @param to último día do tramo actual: hoxe no en curso, o fin do período se non
 * @param previousFrom primeiro día do tramo anterior
 * @param previousTo último día do tramo anterior
 * @param toDate se o período está en curso e se compara só ata hoxe
 */
public record Comparison(
    LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, boolean toDate) {

  public Comparison {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(previousFrom, "previousFrom");
    Objects.requireNonNull(previousTo, "previousTo");
    if (to.isBefore(from) || previousTo.isBefore(previousFrom)) {
      throw new IllegalArgumentException(
          "A span ends before it starts: "
              + from
              + ".."
              + to
              + ", "
              + previousFrom
              + ".."
              + previousTo);
    }
  }
}
