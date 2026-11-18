// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Bloqueos das instalacións, sempre do concello actual. */
public interface BlockRepository {

  /** Os bloqueos da instalación por orde de inicio. */
  List<Block> findByFacility(FacilityId facilityId);

  /** Os bloqueos da instalación con data entre as dúas, incluídas, por orde de inicio. */
  List<Block> findBetween(FacilityId facilityId, LocalDate from, LocalDate to);

  /** Os bloqueos desas instalacións entre as dúas datas, incluídas, nunha lectura (sen N+1). */
  List<Block> findBetween(Collection<FacilityId> facilityIds, LocalDate from, LocalDate to);

  Optional<Block> findById(FacilityId facilityId, BlockId id);

  /** Garda un bloqueo novo. Se pisa outro da instalación → {@code facility.block-overlap}. */
  Block insert(Block block);

  /** Garda os cambios se a versión coincide e sobe a versión; mesmos erros ca {@link #insert}. */
  Block update(Block block);

  void delete(Block block);
}
