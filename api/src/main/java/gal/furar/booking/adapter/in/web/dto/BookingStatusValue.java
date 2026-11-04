// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Estado da reserva («Realizada» calcúlase: {@code done}). */
public enum BookingStatusValue {
  CONFIRMED,
  CANCELLED_BY_PERSON,
  CANCELLED_BY_MUNICIPALITY
}
