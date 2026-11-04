// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Os filtros do panel (plan do dominio 3, §2.3): as instalacións (as de baixa só se teñen reservas
 * recentes) e os seus usos (os arquivados, só se teñen reservas recentes). Por nome en galego.
 */
public record IndicatorFilters(List<Facility> facilities, List<Use> uses) {

  public IndicatorFilters {
    facilities = List.copyOf(facilities);
    uses = List.copyOf(uses);
  }

  /**
   * @param status {@code ACTIVE}, {@code TEMPORARILY_CLOSED} ou {@code WITHDRAWN}
   */
  public record Facility(UUID id, String name, String status) {}

  public record Use(UUID id, UUID facilityId, String name, boolean archived) {}
}
