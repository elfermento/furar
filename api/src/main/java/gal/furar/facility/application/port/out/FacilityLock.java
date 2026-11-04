// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.FacilityId;

/**
 * Bloqueo da instalación (ADR-0022, punto 4): {@code select … for no key update} ata o fin da
 * transacción. Tómao primeiro todo o que cambia a reservabilidade (estado, usos, horario, bloqueos)
 * e, no dominio 2, {@code booking} antes de escribir a ocupación.
 */
public interface FacilityLock {

  /**
   * @return falso se non existe no concello actual
   */
  boolean lock(FacilityId id);
}
