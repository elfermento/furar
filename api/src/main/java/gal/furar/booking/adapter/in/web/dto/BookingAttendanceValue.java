// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Asistencia dunha reserva, á parte do estado e do cobro (ADR-0032). */
public enum BookingAttendanceValue {
  NONE,
  /** Validada: leuse o seu QR ou tecleouse o código. */
  USED,
  /** Non presentado. */
  NO_SHOW
}
