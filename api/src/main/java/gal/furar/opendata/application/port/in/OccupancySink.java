// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import gal.furar.opendata.domain.QuarterRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Onde se escribe a ocupación mentres se le, tramo a tramo (ADR-0034, punto 20): o caso de uso
 * chama a {@link #begin} unha vez, cando xa validou todo, e despois a {@link #write} con cada anaco
 * lido na súa transacción curta.
 */
public interface OccupancySink {

  /**
   * Antes da primeira fila, cando xa se validou todo.
   *
   * @param publishers os concellos que entran
   * @param updatedOn o último cambio da ocupación, se xa o hai
   */
  void begin(List<OpenDataPublisher> publishers, @Nullable LocalDate updatedOn);

  /**
   * Un anaco dun concello.
   *
   * @param uses o nome e o modo de cada uso citado nas filas
   */
  void write(OpenDataPublisher publisher, List<QuarterRow> rows, Map<UUID, UseLabel> uses);

  /** O nome actual dun uso e o seu modo ({@code WHOLE}, {@code PARTS} ou {@code SEATS}). */
  record UseLabel(String name, String rentalMode) {}
}
