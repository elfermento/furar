// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/**
 * Eliminar unha instalación de verdade, co que é só seu (usos, horario, excepcións, bloqueos,
 * deportes, servizos, a súa lista de imaxes e os campos propios). As fotos seguen na biblioteca do
 * concello. Só se nunca tivo reservas; se as tivo, 409 {@code facility.has-bookings} (dáse de
 * baixa). Versión distinta da gardada → {@code common.stale-version} (412).
 */
public interface DeleteFacilityUseCase {

  void delete(DeleteFacilityCommand command);
}
