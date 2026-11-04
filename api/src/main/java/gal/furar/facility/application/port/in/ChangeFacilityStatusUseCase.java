// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/**
 * Cambiar o estado baixo o bloqueo da instalación. Pechar ou dar de baixa con reservas afectadas
 * sen decisión para todas → {@link AffectedBookingsException} (409 con {@code affected[]}).
 */
public interface ChangeFacilityStatusUseCase {

  FacilityView changeStatus(ChangeFacilityStatusCommand command);
}
