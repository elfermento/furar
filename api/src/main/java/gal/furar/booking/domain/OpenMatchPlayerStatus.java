// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** Quen se une a unha partida aberta (ADR-0040): as pendentes non ocupan sitio. */
public enum OpenMatchPlayerStatus {
  /** Pediuno nunha partida «Con aprobación»: decide quen organiza. */
  PENDING,
  /** Está dentro. */
  CONFIRMED,
  /** Quen organiza non a aceptou: non pode pedilo outra vez. */
  REJECTED
}
