// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Un tramo no que a instalación está aberta (ADR-0022, punto 8): o día e os minutos de reloxo de
 * parede en Europe/Madrid (0–1440) e os instantes reais que lles corresponden, co cambio de hora xa
 * aplicado. Inicio incluído e fin excluído.
 *
 * @param alignFromMinute o inicio do tramo do horario (tempada, festivo ou excepción) do que sae
 *     este anaco: un bloqueo corta o tramo, pero os ocos seguen a súa grella (ADR-0025, punto 6).
 *     Igual a {@code startMinute} se ningún bloqueo o cortou polo principio.
 */
public record OpeningInterval(
    LocalDate date,
    int startMinute,
    int endMinute,
    Instant start,
    Instant end,
    int alignFromMinute) {

  public OpeningInterval {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (alignFromMinute > startMinute) {
      throw new IllegalArgumentException("The grid starts after the interval: " + alignFromMinute);
    }
  }

  /** Tramo sen cortar: a grella empeza no seu inicio. */
  public OpeningInterval(
      LocalDate date, int startMinute, int endMinute, Instant start, Instant end) {
    this(date, startMinute, endMinute, start, end, startMinute);
  }
}
