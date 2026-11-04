// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;

/** Ficha de xestión dunha instalación. */
public interface GetFacilityUseCase {

  FacilityView get(FacilityId id);
}
