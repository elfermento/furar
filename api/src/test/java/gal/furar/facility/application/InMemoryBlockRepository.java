// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bloqueos en memoria, separados por concello coma os reais e sen solapes na mesma instalación coma
 * na base. Cada escritura queda en {@code calls} (compartida coa instalación).
 */
public class InMemoryBlockRepository implements BlockRepository {

  private final Map<UUID, Map<BlockId, Block>> byMunicipality = new LinkedHashMap<>();
  private final List<String> calls;

  public InMemoryBlockRepository(List<String> calls) {
    this.calls = calls;
  }

  private Map<BlockId, Block> current() {
    return byMunicipality.computeIfAbsent(TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public List<Block> findByFacility(FacilityId facilityId) {
    return current().values().stream()
        .filter(b -> b.facilityId().equals(facilityId))
        .sorted(Comparator.comparing(Block::start))
        .toList();
  }

  @Override
  public List<Block> findBetween(FacilityId facilityId, LocalDate from, LocalDate to) {
    return findByFacility(facilityId).stream()
        .filter(b -> !b.date().isBefore(from) && !b.date().isAfter(to))
        .toList();
  }

  @Override
  public List<Block> findBetween(Collection<FacilityId> facilityIds, LocalDate from, LocalDate to) {
    return current().values().stream()
        .filter(b -> facilityIds.contains(b.facilityId()))
        .filter(b -> !b.date().isBefore(from) && !b.date().isAfter(to))
        .sorted(Comparator.comparing(Block::start))
        .toList();
  }

  @Override
  public Optional<Block> findById(FacilityId facilityId, BlockId id) {
    return Optional.ofNullable(current().get(id)).filter(b -> b.facilityId().equals(facilityId));
  }

  @Override
  public Block insert(Block block) {
    calls.add("insert-block");
    refuseOverlap(block);
    Block stored = copy(block, 0L);
    current().put(block.id(), stored);
    return stored;
  }

  @Override
  public Block update(Block block) {
    calls.add("update-block");
    Block existing = current().get(block.id());
    if (existing == null) {
      throw new DomainException(ScheduleError.BLOCK_NOT_FOUND);
    }
    if (!Objects.equals(existing.version(), block.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    refuseOverlap(block);
    Block stored = copy(block, Objects.requireNonNull(block.version()) + 1);
    current().put(block.id(), stored);
    return stored;
  }

  @Override
  public void delete(Block block) {
    calls.add("delete-block");
    current().remove(block.id());
  }

  private void refuseOverlap(Block block) {
    boolean overlaps =
        findByFacility(block.facilityId()).stream()
            .anyMatch(b -> !b.id().equals(block.id()) && b.overlaps(block));
    if (overlaps) {
      throw new DomainException(ScheduleError.BLOCK_OVERLAP);
    }
  }

  private static Block copy(Block b, long version) {
    return Block.rehydrate(
        b.id(),
        b.facilityId(),
        b.date(),
        b.interval(),
        b.start(),
        b.end(),
        b.reason(),
        b.createdBy(),
        version,
        b.createdAt(),
        b.updatedAt());
  }
}
