// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityPermission;
import gal.furar.facility.NoBookingImpact;
import gal.furar.facility.application.port.in.schedule.CreateBlockCommand;
import gal.furar.facility.application.port.in.schedule.DeleteBlockCommand;
import gal.furar.facility.application.port.in.schedule.ManageBlocksUseCase;
import gal.furar.facility.application.port.in.schedule.UpdateBlockCommand;
import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.application.service.AffectedBookingsGate;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso dos bloqueos (D1-16). Cada escritura toma primeiro o bloqueo da instalación
 * (ADR-0022, punto 4): por iso o solape con outro bloqueo compróbase aquí, co bloqueo que xa ten
 * esas horas, antes de que o diga a base (P7). Un bloqueo novo ou un cambio de período que bloquea
 * horas que antes non bloqueaba pregunta a {@link BookingImpact} ({@link FacilityChange.Block}) e
 * abre o modal de reservas afectadas se hai (punto 6). Cambiar só o motivo, acurtar o bloqueo ou
 * borralo non deixa fóra ningunha reserva: sen modal. Cada escritura queda no rexistro de
 * actividade (ADR-0020).
 */
@Service
public class BlockService implements ManageBlocksUseCase {

  static final String CREATE = "facility.block-create";
  static final String UPDATE = "facility.block-update";
  static final String DELETE = "facility.block-delete";

  private final FacilityRepository facilities;
  private final BlockRepository blocks;
  private final FacilityLock lock;
  private final AffectedBookingsGate gate;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final Clock clock;

  /** Un só {@link BookingImpact} (ADR-0005): sen ningún, {@link NoBookingImpact}. */
  @Autowired
  public BlockService(
      FacilityRepository facilities,
      BlockRepository blocks,
      FacilityLock lock,
      ObjectProvider<BookingImpact> bookingImpact,
      CurrentActor currentActor,
      AuditLog audit,
      Clock clock) {
    this(
        facilities,
        blocks,
        lock,
        bookingImpact.getIfAvailable(NoBookingImpact::new),
        currentActor,
        audit,
        clock);
  }

  public BlockService(
      FacilityRepository facilities,
      BlockRepository blocks,
      FacilityLock lock,
      BookingImpact impact,
      CurrentActor currentActor,
      AuditLog audit,
      Clock clock) {
    this.facilities = facilities;
    this.blocks = blocks;
    this.lock = lock;
    this.gate = new AffectedBookingsGate(impact);
    this.currentActor = currentActor;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public List<Block> blocks(FacilityId facilityId) {
    if (facilities.findById(facilityId).isEmpty()) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
    return blocks.findByFacility(facilityId);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public Block block(FacilityId facilityId, BlockId blockId) {
    return blocks
        .findById(facilityId, blockId)
        .orElseThrow(() -> new DomainException(ScheduleError.BLOCK_NOT_FOUND));
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public Block create(CreateBlockCommand command) {
    return audited(
        CREATE,
        command.facilityId(),
        null,
        () -> {
          lockFacility(command.facilityId());
          Block block =
              Block.create(
                  BlockId.newId(), command.facilityId(), command.spec(), actor(), clock.instant());
          refuseOverlap(block);
          return guarded(
              new FacilityChange.Block(
                  block.facilityId().value(), null, block.start(), block.end()),
              command.decisions(),
              () -> blocks.insert(block));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public Block update(UpdateBlockCommand command) {
    return audited(
        UPDATE,
        command.facilityId(),
        command.blockId(),
        () -> {
          lockFacility(command.facilityId());
          Block current =
              current(command.facilityId(), command.blockId(), command.expectedVersion());
          Block changed = current.update(command.spec(), clock.instant());
          refuseOverlap(changed);
          if (changed.within(current)) {
            // Só motivo, ou menos horas: as reservas que xa respectaba seguen igual.
            return blocks.update(changed);
          }
          return guarded(
              new FacilityChange.Block(
                  changed.facilityId().value(),
                  changed.id().value(),
                  changed.start(),
                  changed.end()),
              command.decisions(),
              () -> blocks.update(changed));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public void delete(DeleteBlockCommand command) {
    audited(
        DELETE,
        command.facilityId(),
        command.blockId(),
        () -> {
          lockFacility(command.facilityId());
          Block current =
              current(command.facilityId(), command.blockId(), command.expectedVersion());
          blocks.delete(current);
          return current;
        });
  }

  /** Pregunta polas reservas afectadas, garda e aplica as decisións (ADR-0022, punto 6). */
  private Block guarded(
      FacilityChange.Block change, List<BookingDecision> decisions, Supplier<Block> write) {
    List<BookingDecision> accepted = gate.check(change, decisions);
    Block saved = write.get();
    gate.apply(change, accepted);
    return saved;
  }

  /**
   * Xa baixo o bloqueo: ningunha outra escritura pode colarse antes do gardado. Só os bloqueos do
   * mesmo día poden pisarse (cada un é dun día; o seguinte empeza onde acaba este como moito).
   */
  private void refuseOverlap(Block block) {
    for (Block other : blocks.findBetween(block.facilityId(), block.date(), block.date())) {
      if (!other.id().equals(block.id()) && other.overlaps(block)) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("blockId", other.id().value().toString());
        params.put("date", other.date().toString());
        params.put("startMinute", other.interval().startMinute());
        params.put("endMinute", other.interval().endMinute());
        throw new DomainException(ScheduleError.BLOCK_OVERLAP, params);
      }
    }
  }

  /** Quen crea o bloqueo (claim {@code sub}), coma no rexistro de actividade. */
  private String actor() {
    return currentActor.current().map(Actor::subject).orElse("anonymous");
  }

  private void lockFacility(FacilityId id) {
    if (!lock.lock(id)) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
  }

  private Block current(FacilityId facilityId, BlockId id, long expectedVersion) {
    Block current =
        blocks
            .findById(facilityId, id)
            .orElseThrow(() -> new DomainException(ScheduleError.BLOCK_NOT_FOUND));
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito coa instalación, o bloqueo e o día; o fallo
   * co código. Se o rexistro do fallo non se pode escribir, mantense o erro orixinal.
   */
  private Block audited(
      String action, FacilityId facilityId, @Nullable BlockId blockId, Supplier<Block> work) {
    TenantRef tenant = TenantContext.require();
    try {
      Block block = work.get();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      details.put("blockId", block.id().value().toString());
      details.put("date", block.date().toString());
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return block;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      if (blockId != null) {
        details.put("blockId", blockId.value().toString());
      }
      details.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(action, Outcome.FAILURE, tenant.id(), tenant.slug(), details));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }
}
