// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import java.util.List;

/** Lista, lectura, alta, edición e borrado dos bloqueos; as escrituras, baixo o bloqueo. */
public interface ManageBlocksUseCase {

  /** Os bloqueos da instalación por orde de inicio. */
  List<Block> blocks(FacilityId facilityId);

  Block block(FacilityId facilityId, BlockId blockId);

  Block create(CreateBlockCommand command);

  Block update(UpdateBlockCommand command);

  void delete(DeleteBlockCommand command);
}
