// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * API pública síncrona dos indicadores para outros módulos (ADR-0034, punto 4; A19): a ocupación
 * por tramos de 15 minutos, coa mesma ponderación de cada modo ca o panel. Sempre no concello
 * actual; non comproba permisos.
 */
public interface IndicatorsQueries {

  /** Días como moito nunha lectura. */
  int MAX_DAYS = 31;

  /**
   * A ocupación de cada instalación e día (Europe/Madrid, os dous extremos incluídos) por tramos de
   * 15 minutos desde a medianoite, en tempo real (96 tramos; 92 e 100 os días do cambio de hora).
   * Só os días nos que a instalación abriu algún tramo.
   *
   * @throws IllegalArgumentException se {@code to} é anterior a {@code from} ou o rango pasa de
   *     {@value #MAX_DAYS} días
   */
  List<DayQuarters> quarterOccupancy(LocalDate from, LocalDate to, Collection<UUID> facilityIds);

  /**
   * Un día dunha instalación.
   *
   * @param quarters un valor por tramo: 0–100 se estaba aberta, {@code null} se estaba pechada ou
   *     bloqueada
   * @param uses os usos distintos que a ocuparon ese día, na orde en que aparecen
   * @param quarterUses por tramo, a posición (desde 1) en {@code uses} do uso que o ocupaba; {@code
   *     null} se estaba libre, pechada ou bloqueada
   */
  record DayQuarters(
      UUID facilityId,
      LocalDate day,
      List<Integer> quarters,
      List<UUID> uses,
      List<Integer> quarterUses) {

    public DayQuarters {
      Objects.requireNonNull(facilityId, "facilityId");
      Objects.requireNonNull(day, "day");
      // Con nulos: List.copyOf non os admite.
      quarters = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(quarters));
      uses = List.copyOf(uses);
      quarterUses = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(quarterUses));
      if (quarters.size() != quarterUses.size()) {
        throw new IllegalArgumentException("quarters and quarterUses differ in size");
      }
    }
  }
}
