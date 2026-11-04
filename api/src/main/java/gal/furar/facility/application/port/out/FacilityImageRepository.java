// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.PhotoId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Porto de saída das listas de imaxes das instalacións. Sempre dentro do concello actual. */
public interface FacilityImageRepository {

  /** A lista gardada ({@link FacilityImages#NONE} se non hai). */
  FacilityImages load(FacilityId facility);

  /** As listas de varias instalacións nunha consulta (só as que teñen algunha). */
  Map<FacilityId, FacilityImages> loadAll(Collection<FacilityId> facilities);

  /** Substitúe a lista da instalación. */
  void replace(FacilityId facility, FacilityImages images);

  /** Que instalacións usan cada foto da biblioteca. */
  Map<PhotoId, List<FacilityId>> photoUsage();

  /** As instalacións que usan unha foto. */
  List<FacilityId> usingPhoto(PhotoId photo);
}
