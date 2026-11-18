// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import java.util.List;

/**
 * Unha instalación enteira para o ficheiro furar-export (ADR-0018): a ficha (con todos os campos
 * propios e o metadato das fotos), os usos (tamén arquivados) coas partes arquivadas á parte, as
 * tempadas coa semana, as excepcións e os bloqueos. Nunca se garda.
 */
public record FacilityExport(
    FacilityView facility,
    List<FacilityUse> uses,
    List<ArchivedUsePart> archivedParts,
    List<Season> seasons,
    List<ScheduleException> exceptions,
    List<Block> blocks) {

  public FacilityExport {
    uses = List.copyOf(uses);
    archivedParts = List.copyOf(archivedParts);
    seasons = List.copyOf(seasons);
    exceptions = List.copyOf(exceptions);
    blocks = List.copyOf(blocks);
  }
}
