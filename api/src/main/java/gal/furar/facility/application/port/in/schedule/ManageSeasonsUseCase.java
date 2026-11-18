// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.schedule.Season;

/** Alta, edición e borrado das tempadas dunha instalación, sempre baixo o seu bloqueo. */
public interface ManageSeasonsUseCase {

  Season create(CreateSeasonCommand command);

  Season update(UpdateSeasonCommand command);

  void delete(DeleteSeasonCommand command);
}
