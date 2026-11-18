// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;

/**
 * A xestión oculta ou volve mostrar a partida aberta dunha reserva (ADR-0040, punto 8; permiso
 * {@code booking.open-matches}). Oculta: fóra de todas as listas, a súa páxina pública dá 404,
 * ninguén novo se une e a tarefa do prazo non a cancela. Queda no rexistro de actividade sen
 * título, descrición nin nomes.
 */
public interface ManageOpenMatchUseCase {

  /**
   * Oculta ou mostra; repetir non cambia nada. Sen partida, 404 {@code booking.match-not-found}.
   */
  OpenMatchSummary hide(BookingId bookingId, boolean hidden);
}
