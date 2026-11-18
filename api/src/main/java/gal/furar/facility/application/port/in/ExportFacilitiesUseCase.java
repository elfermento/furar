// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.CustomSport;
import java.util.List;

/**
 * Todo o do módulo no concello actual, para o ficheiro furar-export (ADR-0018). O que falta para
 * deixar o borrador non vai: calcúlase de novo ao importar.
 */
public interface ExportFacilitiesUseCase {

  /** Todas as instalacións, por data de alta, con todo o seu. */
  List<FacilityExport> exportAll();

  /** Os deportes do concello, tamén os que xa non usa ningunha instalación, por data de alta. */
  List<CustomSport> exportCustomSports();
}
