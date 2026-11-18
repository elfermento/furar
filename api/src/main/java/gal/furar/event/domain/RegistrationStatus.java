// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

/**
 * Estado dunha inscrición: pide praza ({@code PENDING}, só «Con aprobación»), ten praza ({@code
 * CONFIRMED}: apuntada directamente, aceptada ou confirmada desde a lista de espera), o concello
 * non a aceptou ({@code REJECTED}), espera na lista ({@code WAITING}, ADR-0041: o evento está
 * completo ou xa hai lista, e non leva aprobación) ou foi avisada de que hai unha praza libre
 * ({@code NOTIFIED}) e pode confirmala mentres siga libre. Só ocupan praza as confirmadas: a praza
 * libre lévaa a primeira avisada que a confirme.
 */
public enum RegistrationStatus {
  PENDING,
  CONFIRMED,
  REJECTED,
  WAITING,
  NOTIFIED;

  /** Se está na lista de espera (esperando ou avisada). */
  public boolean waitlisted() {
    return this == WAITING || this == NOTIFIED;
  }
}
