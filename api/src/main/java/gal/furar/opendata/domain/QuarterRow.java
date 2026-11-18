// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha fila da ocupación publicada (ADR-0034, punto 16): unha instalación, un día e un tramo de 15
 * minutos no que estaba aberta, co uso que a ocupaba (ou ningún) e a parte reservada.
 *
 * @param start o inicio do tramo, con desfase de Europe/Madrid
 * @param end 15 minutos despois
 * @param useId o uso que a ocupaba; nulo se estaba libre
 * @param value de 0 a 100
 */
public record QuarterRow(
    UUID facilityId,
    LocalDate day,
    OffsetDateTime start,
    OffsetDateTime end,
    @Nullable UUID useId,
    int value) {

  /**
   * Desprega un día gardado en filas: unha por tramo con valor, na orde en que pasaron (o día do
   * cambio de hora de outubro, a hora repetida sae dúas veces cos dous desfases).
   *
   * @param quarters o valor de cada tramo, ou nulo se estaba pechada ou bloqueada
   * @param uses os usos do día
   * @param quarterUses por tramo, a posición (desde 1) en {@code uses}, ou nulo
   */
  public static List<QuarterRow> expand(
      UUID facilityId,
      LocalDate day,
      List<? extends @Nullable Number> quarters,
      List<UUID> uses,
      List<? extends @Nullable Number> quarterUses) {
    ZonedDateTime start = day.atStartOfDay(OccupancyRange.ZONE);
    List<QuarterRow> rows = new ArrayList<>();
    for (int i = 0; i < quarters.size(); i++) {
      Number value = quarters.get(i);
      if (value == null) {
        continue;
      }
      // En tempo real: o día do cambio de hora o desfase cambia a metade.
      ZonedDateTime from = start.toInstant().plusSeconds(900L * i).atZone(OccupancyRange.ZONE);
      ZonedDateTime to = from.toInstant().plusSeconds(900).atZone(OccupancyRange.ZONE);
      Number use = i < quarterUses.size() ? quarterUses.get(i) : null;
      rows.add(
          new QuarterRow(
              facilityId,
              day,
              from.toOffsetDateTime(),
              to.toOffsetDateTime(),
              use == null ? null : uses.get(use.intValue() - 1),
              value.intValue()));
    }
    return rows;
  }
}
