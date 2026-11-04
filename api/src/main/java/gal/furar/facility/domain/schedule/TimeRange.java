// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

/**
 * Tramo dun día en minutos de reloxo de parede desde a medianoite (ADR-0022, punto 8): inicio
 * incluído e fin excluído, así que dous tramos seguidos (8:00–14:00 e 14:00–20:00) non se pisan.
 * Sen comprobar: {@link #isValid()} di se cumpre as regras e {@link WeekHours} só admite tramos
 * válidos.
 */
public record TimeRange(int startMinute, int endMinute) implements Comparable<TimeRange> {

  /** 24:00 (P12: pódese pechar á medianoite). */
  public static final int DAY_END = 1440;

  /** As horas van de cuarto en cuarto. */
  public static final int STEP = 15;

  /** Fin despois do inicio, sen pasar da medianoite e en múltiplos de 15. */
  public boolean isValid() {
    return startMinute >= 0
        && startMinute < endMinute
        && endMinute <= DAY_END
        && startMinute % STEP == 0
        && endMinute % STEP == 0;
  }

  public boolean overlaps(TimeRange other) {
    return startMinute < other.endMinute && other.startMinute < endMinute;
  }

  @Override
  public int compareTo(TimeRange other) {
    int byStart = Integer.compare(startMinute, other.startMinute);
    return byStart != 0 ? byStart : Integer.compare(endMinute, other.endMinute);
  }
}
