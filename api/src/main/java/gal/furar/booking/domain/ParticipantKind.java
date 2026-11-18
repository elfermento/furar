// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** Quen ocupa unha praza dunha reserva (ADR-0031, punto 5). */
public enum ParticipantKind {
  /** Quen ten a reserva: o seu nome é o da reserva. */
  HOLDER,
  /** Unha persoa a cargo gardada na conta: a reserva queda ligada á súa ficha. */
  DEPENDENT,
  /** Un acompañante que o persoal escribe no mostrador só para esa reserva. */
  COMPANION
}
