// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UseId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Porto de saída dos usos. Sempre dentro do concello actual. */
public interface FacilityUseRepository {

  /** Os usos vivos da instalación, por orde de alta. */
  List<FacilityUse> findActive(FacilityId facilityId);

  /** Todos os usos da instalación, tamén os arquivados, por orde de alta (portabilidade). */
  List<FacilityUse> findAll(FacilityId facilityId);

  /** As partes arquivadas dos usos da instalación, por uso e posición (portabilidade). */
  List<ArchivedUsePart> findArchivedParts(FacilityId facilityId);

  /** Garda partes xa arquivadas dun ficheiro furar-export, despois do seu uso. */
  void insertArchivedParts(List<ArchivedUsePart> parts);

  /** O uso desta instalación, tamén arquivado (só as súas partes vivas). */
  Optional<FacilityUse> findById(FacilityId facilityId, UseId useId);

  /** Un uso do concello actual, de calquera instalación (tamén arquivado). */
  Optional<FacilityUse> findById(UseId useId);

  /** Todos os usos do concello actual, tamén os arquivados, por orde de alta. */
  List<FacilityUse> findAllInMunicipality();

  /** Os usos do concello actual con eses ids (tamén arquivados), cunha lectura por táboa. */
  List<FacilityUse> findAllById(Collection<UseId> ids);

  /**
   * Garda un uso novo. Nome repetido na instalación → {@code facility.use-name-taken}; nomes de
   * partes repetidos → {@code facility.use-part-name-taken}.
   */
  FacilityUse insert(FacilityUse use);

  /**
   * Garda os cambios se a versión coincide; se non, {@code common.stale-version}. As partes que xa
   * non están arquívanse e os períodos substitúense enteiros. Mesmos erros de nome ca {@link
   * #insert}.
   */
  FacilityUse update(FacilityUse use);
}
