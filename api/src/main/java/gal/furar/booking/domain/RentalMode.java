// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** Como se aluga o uso reservado (ADR-0012): enteiro, por partes ou por prazas. */
public enum RentalMode {
  WHOLE,
  PARTS,
  SEATS
}
