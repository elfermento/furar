// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Que hai un día que non se crea por choque (ADR-0039, punto 8). */
public enum SeriesConflictKindValue {
  OTHER_USE_EXCLUSIVE,
  OUT_OF_HOURS,
  BLOCK,
  BOOKING,
  SESSION
}
