// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

/** Como se aluga un uso (especificación, bloque 1, «Usos»). */
public enum RentalMode {
  /** Enteira: unha reserva por franxa. */
  WHOLE,
  /** Por partes con nome (Campo A/B): unha reserva por parte e franxa (P3). */
  PARTS,
  /** Por prazas: ata N persoas por franxa. */
  SEATS
}
