// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un uso dunha instalación para outros módulos (A19: só tipos da raíz e de Java): o que precisa
 * quen reserva para comprobar o uso e calcular os ocos.
 *
 * @param rentalMode {@code WHOLE}, {@code PARTS} ou {@code SEATS}
 * @param capacity prazas, só con {@code SEATS}
 * @param parts as partes vivas na súa orde, só con {@code PARTS}
 * @param periods os rangos de datas (extremos incluídos); baleira = «sempre»
 * @param weekdays máscara de días da semana (luns = 1 … domingo = 64)
 * @param archived se o uso xa se quitou (non se reserva)
 */
public record UseSummary(
    UUID id,
    UUID facilityId,
    String name,
    String rentalMode,
    @Nullable Integer capacity,
    int slotMinutes,
    boolean blocksOthers,
    List<Part> parts,
    List<DateRange> periods,
    int weekdays,
    boolean archived,
    UseTerms terms) {

  public UseSummary {
    Objects.requireNonNull(terms, "terms");
    parts = List.copyOf(parts);
    periods = List.copyOf(periods);
  }

  /**
   * Se o uso vale ese día: vivo, nas súas datas e nun dos seus días da semana. Non mira o horario
   * nin os usos que bloquean ({@link FacilityQueries#exclusiveUseOn}).
   */
  public boolean availableOn(LocalDate date) {
    if (archived || (weekdays & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) {
      return false;
    }
    return periods.isEmpty()
        || periods.stream().anyMatch(p -> !date.isBefore(p.from()) && !date.isAfter(p.to()));
  }

  /** Parte viva dun uso. */
  public record Part(UUID id, String name) {}

  /** Rango de datas cos dous extremos incluídos. */
  public record DateRange(LocalDate from, LocalDate to) {}
}
