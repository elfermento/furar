// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Adaptador JPA dos bloqueos, sempre dentro dun concello (A7b). */
@Component
class BlockPersistenceAdapter extends TenantScopedPersistenceAdapter implements BlockRepository {

  /** Dous bloqueos da instalación non se pisan (o servizo xa o comproba baixo o bloqueo). */
  static final String OVERLAP_EXCLUSION = "block_overlap_ex";

  private final BlockJpaRepository blocks;

  BlockPersistenceAdapter(BlockJpaRepository blocks) {
    this.blocks = blocks;
  }

  @Override
  public List<Block> findByFacility(FacilityId facilityId) {
    tenant();
    return blocks.findByFacilityIdOrderByStartsAtAsc(facilityId.value()).stream()
        .map(BlockPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Block> findBetween(FacilityId facilityId, LocalDate from, LocalDate to) {
    tenant();
    return blocks
        .findByFacilityIdAndDateBetweenOrderByStartsAtAsc(facilityId.value(), from, to)
        .stream()
        .map(BlockPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Block> findBetween(Collection<FacilityId> facilityIds, LocalDate from, LocalDate to) {
    tenant();
    if (facilityIds.isEmpty()) {
      return List.of();
    }
    return blocks
        .findByFacilityIdInAndDateBetweenOrderByStartsAtAsc(
            facilityIds.stream().map(FacilityId::value).toList(), from, to)
        .stream()
        .map(BlockPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<Block> findById(FacilityId facilityId, BlockId id) {
    tenant();
    return blocks
        .findByIdAndFacilityId(id.value(), facilityId.value())
        .map(BlockPersistenceAdapter::toDomain);
  }

  @Override
  public Block insert(Block block) {
    tenant();
    return translated(
        () -> {
          BlockEntity entity =
              new BlockEntity(
                  block.id().value(),
                  block.facilityId().value(),
                  block.createdBy(),
                  block.createdAt());
          apply(entity, block);
          return toDomain(blocks.saveAndFlush(entity));
        });
  }

  @Override
  public Block update(Block block) {
    tenant();
    BlockEntity entity =
        blocks
            .findByIdAndFacilityId(block.id().value(), block.facilityId().value())
            .orElseThrow(() -> new DomainException(ScheduleError.BLOCK_NOT_FOUND));
    if (!Objects.equals(entity.version(), block.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return translated(
        () -> {
          apply(entity, block);
          return toDomain(blocks.saveAndFlush(entity));
        });
  }

  @Override
  public void delete(Block block) {
    tenant();
    blocks
        .findByIdAndFacilityId(block.id().value(), block.facilityId().value())
        .ifPresent(
            entity -> {
              blocks.delete(entity);
              blocks.flush();
            });
  }

  private static void apply(BlockEntity entity, Block block) {
    entity.apply(
        block.date(),
        block.interval().startMinute(),
        block.interval().endMinute(),
        block.start(),
        block.end(),
        block.reason(),
        block.updatedAt());
  }

  private static Block toDomain(BlockEntity e) {
    return Block.rehydrate(
        new BlockId(e.getId()),
        new FacilityId(e.facilityId()),
        e.date(),
        new TimeRange(e.startMinute(), e.endMinute()),
        e.startsAt(),
        e.endsAt(),
        e.reason(),
        e.createdBy(),
        e.version() == null ? 0 : e.version(),
        e.createdAt(),
        e.updatedAt());
  }

  /** O solape da base tradúcese ao código do dominio; o resto sae como está. */
  private static Block translated(Supplier<Block> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(OVERLAP_EXCLUSION)) {
        throw new DomainException(ScheduleError.BLOCK_OVERLAP);
      }
      throw e;
    }
  }
}
