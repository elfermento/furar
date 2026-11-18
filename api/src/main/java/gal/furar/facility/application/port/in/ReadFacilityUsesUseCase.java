// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UseId;
import java.util.List;

/** Lectura de xestión dos usos dunha instalación. */
public interface ReadFacilityUsesUseCase {

  /** Os usos vivos (sen arquivados), por orde de alta. */
  List<FacilityUse> list(FacilityId facilityId);

  /** Un uso, tamén arquivado. */
  FacilityUse get(FacilityId facilityId, UseId useId);
}
