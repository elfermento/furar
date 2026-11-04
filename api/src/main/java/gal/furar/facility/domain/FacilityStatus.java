// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

/**
 * Estado da instalación (especificación, bloque 1, «Estados e baixa»). O borrador non é un estado:
 * calcúlase co que falta ({@link Requirement}, ADR-0022).
 */
public enum FacilityStatus {
  /** Activa: reservable se non está en borrador. */
  ACTIVE,
  /** Pechada temporalmente: vese, pero non se reserva (P16). */
  TEMPORARILY_CLOSED,
  /** De baixa: desaparece para a veciñanza e conserva o historial. */
  WITHDRAWN
}
