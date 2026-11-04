// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityUse;

/**
 * Alta, edición e arquivo dos usos dunha instalación (D1-12). Todo baixo o bloqueo da instalación
 * (ADR-0022, punto 4).
 */
public interface ManageFacilityUsesUseCase {

  FacilityUse create(CreateUseCommand command);

  FacilityUse update(UpdateUseCommand command);

  FacilityUse archive(ArchiveUseCommand command);
}
