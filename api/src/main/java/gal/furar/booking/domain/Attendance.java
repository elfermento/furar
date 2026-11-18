// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/**
 * Asistencia dunha reserva, á parte do estado e do cobro (ADR-0032, punto 1): unha reserva validada
 * ou con falta segue confirmada e ocupando a súa hora.
 */
public enum Attendance {
  /** Sen marca. */
  NONE,
  /** Validada: leuse o seu QR ou tecleouse o código. */
  USED,
  /** Non presentado. */
  NO_SHOW
}
