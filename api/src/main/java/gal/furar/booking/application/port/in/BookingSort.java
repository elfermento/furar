// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

/** Orde da lista de xestión: as máis recentes primeiro ou por día e hora. */
public enum BookingSort {
  /** Por cando se reservaron, da última á primeira. */
  RECENT,
  /** Por día e hora da reserva, da máis temperá á máis tardía. */
  START
}
