// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import java.util.List;

/** Carga as filas dun ficheiro furar-export no concello novo (ADR-0018). */
public interface ImportFacilitiesUseCase {

  /** Os deportes do concello (sección propia), antes ca as instalacións que os usan. */
  void importCustomSports(List<RestoreCustomSportCommand> customSports);

  /**
   * @param customSports os deportes do concello aos que se refiren as filas; os que xa entraron coa
   *     súa sección non se repiten (un ficheiro sen esa sección tráeos só aquí)
   */
  void importAll(List<RestoreCustomSportCommand> customSports, List<RestoreFacilityCommand> rows);
}
