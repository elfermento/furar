// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityPermission;
import gal.furar.facility.NoBookingImpact;
import gal.furar.facility.application.port.in.ArchiveUseCommand;
import gal.furar.facility.application.port.in.CreateUseCommand;
import gal.furar.facility.application.port.in.ManageFacilityUsesUseCase;
import gal.furar.facility.application.port.in.ReadFacilityUsesUseCase;
import gal.furar.facility.application.port.in.UpdateUseCommand;
import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.SportRef;
import gal.furar.facility.domain.UseError;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.shared.audit.AuditChanges;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso dos usos da instalación. Cada escritura toma primeiro o bloqueo da instalación
 * (ADR-0022, punto 4), que serializa todas as escrituras de usos: por iso a regra «dous usos que
 * bloquean non se pisan» compróbase aquí e non na base. Despois, con {@link BookingImpact}: un
 * cambio que deixaría mal reservas futuras do uso → 409 {@code facility.use-has-bookings}; un uso
 * que pasa a bloquear os demais → modal de reservas afectadas (punto 6). Cada escritura queda no
 * rexistro de actividade (ADR-0020).
 */
@Service
public class FacilityUseService implements ManageFacilityUsesUseCase, ReadFacilityUsesUseCase {

  static final String CREATE = "facility.use-create";
  static final String UPDATE = "facility.use-update";
  static final String ARCHIVE = "facility.use-archive";

  private final FacilityRepository facilities;
  private final FacilityUseRepository uses;
  private final FacilityLock lock;
  private final BookingImpact impact;
  private final AffectedBookingsGate gate;
  private final AuditLog audit;
  private final Clock clock;

  /** Un só {@link BookingImpact} (ADR-0005): sen ningún, {@link NoBookingImpact}. */
  @Autowired
  public FacilityUseService(
      FacilityRepository facilities,
      FacilityUseRepository uses,
      FacilityLock lock,
      ObjectProvider<BookingImpact> bookingImpact,
      AuditLog audit,
      Clock clock) {
    this(facilities, uses, lock, bookingImpact.getIfAvailable(NoBookingImpact::new), audit, clock);
  }

  public FacilityUseService(
      FacilityRepository facilities,
      FacilityUseRepository uses,
      FacilityLock lock,
      BookingImpact impact,
      AuditLog audit,
      Clock clock) {
    this.facilities = facilities;
    this.uses = uses;
    this.lock = lock;
    this.impact = impact;
    this.gate = new AffectedBookingsGate(impact);
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public List<FacilityUse> list(FacilityId facilityId) {
    if (facilities.findById(facilityId).isEmpty()) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
    return uses.findActive(facilityId);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public FacilityUse get(FacilityId facilityId, UseId useId) {
    return uses.findById(facilityId, useId)
        .orElseThrow(() -> new DomainException(UseError.NOT_FOUND));
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityUse create(CreateUseCommand command) {
    return audited(
        CREATE,
        command.facilityId(),
        null,
        changes -> {
          lockFacility(command.facilityId());
          FacilityUse use =
              FacilityUse.create(
                  UseId.newId(), command.facilityId(), command.spec(), clock.instant());
          return save(null, use, command.decisions());
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityUse update(UpdateUseCommand command) {
    return audited(
        UPDATE,
        command.facilityId(),
        command.useId(),
        changes -> {
          lockFacility(command.facilityId());
          FacilityUse current =
              current(command.facilityId(), command.useId(), command.expectedVersion());
          FacilityUse changed = current.update(command.spec(), clock.instant());
          FacilityChange.@Nullable UseChanged useChanged =
              changed.changesBookingsOf(current)
                  ? new FacilityChange.UseChanged(facilityUuid(current), useUuid(current))
                  : null;
          if (useChanged != null) {
            refuseIfBooked(useChanged);
          }
          FacilityUse saved = save(current, changed, command.decisions());
          if (useChanged != null) {
            // Sen reservas que tocar, pero quen esperaba por unha hora deste uso xa non a ten.
            impact.apply(useChanged, List.of());
          }
          if (useChanged == null && datesChanged(current, saved)) {
            // As reservas quedan; as esperas por días nos que o uso xa non vale, non.
            impact.apply(
                new FacilityChange.UseDatesChanged(facilityUuid(saved), useUuid(saved)), List.of());
          }
          resizeSeats(current, saved);
          changes.compare(FacilityAuditSnapshots.of(current), FacilityAuditSnapshots.of(saved));
          return saved;
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityUse archive(ArchiveUseCommand command) {
    return audited(
        ARCHIVE,
        command.facilityId(),
        command.useId(),
        changes -> {
          lockFacility(command.facilityId());
          FacilityUse current =
              current(command.facilityId(), command.useId(), command.expectedVersion());
          if (current.isArchived()) {
            return current;
          }
          FacilityChange.UseArchived archived =
              new FacilityChange.UseArchived(facilityUuid(current), useUuid(current));
          refuseIfBooked(archived);
          FacilityUse saved = uses.update(current.archive(clock.instant()));
          impact.apply(archived, List.of());
          return saved;
        });
  }

  private static boolean datesChanged(FacilityUse before, FacilityUse after) {
    return before.availability() != after.availability()
        || before.weekdays() != after.weekdays()
        || !before.periods().equals(after.periods());
  }

  /**
   * Comprobacións que miran os outros usos e as reservas, e gardado. Xa baixo o bloqueo: ningunha
   * outra escritura de usos desta instalación pode colarse entre a comprobación e o gardado.
   */
  private FacilityUse save(
      @Nullable FacilityUse previous, FacilityUse use, List<BookingDecision> decisions) {
    checkSports(use);
    for (FacilityUse other : uses.findActive(use.facilityId())) {
      if (use.exclusiveOverlapWith(other)) {
        throw new DomainException(UseError.EXCLUSIVE_USE_OVERLAP, Map.of("name", other.name()));
      }
    }
    List<UsePeriod> blocked = use.newlyBlockedSince(previous);
    FacilityChange.@Nullable ExclusiveUse change =
        blocked.isEmpty()
            ? null
            : new FacilityChange.ExclusiveUse(
                facilityUuid(use),
                useUuid(use),
                blocked.stream().map(p -> new FacilityChange.Dates(p.from(), p.to())).toList(),
                use.weekdays());
    List<BookingDecision> accepted = change == null ? List.of() : gate.check(change, decisions);
    FacilityUse saved = previous == null ? uses.insert(use) : uses.update(use);
    if (change != null) {
      gate.apply(change, accepted);
    }
    return saved;
  }

  /** Os deportes do uso son da súa instalación (`sports[i]` inválido se non). */
  private void checkSports(FacilityUse use) {
    if (use.sports().isEmpty()) {
      return;
    }
    List<String> known =
        facilities
            .findById(use.facilityId())
            .map(f -> f.details().sports().stream().map(SportRef::key).toList())
            .orElse(List.of());
    List<FieldViolation> invalid = new ArrayList<>();
    for (int i = 0; i < use.sports().size(); i++) {
      if (!known.contains(use.sports().get(i))) {
        invalid.add(FieldViolation.of("sports[" + i + "]", ValidationError.INVALID));
      }
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(UseError.INVALID, Map.of(), invalid);
    }
  }

  /**
   * Se un uso por prazas cambia de prazas, {@code booking} leva a capacidade nova aos seus
   * contadores (ADR-0012): baixo o mesmo bloqueo e na mesma transacción, despois de gardar.
   */
  private void resizeSeats(FacilityUse previous, FacilityUse saved) {
    Integer capacity = saved.capacity();
    if (saved.rentalMode() == RentalMode.SEATS
        && previous.rentalMode() == RentalMode.SEATS
        && capacity != null
        && !capacity.equals(previous.capacity())) {
      impact.apply(
          new FacilityChange.SeatsChanged(facilityUuid(saved), useUuid(saved), capacity),
          List.of());
    }
  }

  /** Se o cambio toca reservas futuras do uso, non se fai; o xestor pode crear outro uso. */
  private void refuseIfBooked(FacilityChange change) {
    int booked = impact.affectedBy(change).size();
    if (booked > 0) {
      throw new DomainException(UseError.HAS_BOOKINGS, Map.of("count", booked));
    }
  }

  private void lockFacility(FacilityId id) {
    if (!lock.lock(id)) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
  }

  private FacilityUse current(FacilityId facilityId, UseId useId, long expectedVersion) {
    FacilityUse current =
        uses.findById(facilityId, useId).orElseThrow(() -> new DomainException(UseError.NOT_FOUND));
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  private static UUID facilityUuid(FacilityUse use) {
    return use.facilityId().value();
  }

  private static UUID useUuid(FacilityUse use) {
    return use.id().value();
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito coa instalación e o uso; o fallo co código.
   * Se o rexistro do fallo non se pode escribir, mantense o erro orixinal.
   */
  private FacilityUse audited(
      String action,
      FacilityId facilityId,
      @Nullable UseId useId,
      Function<AuditChanges, FacilityUse> work) {
    TenantRef tenant = TenantContext.require();
    try {
      AuditChanges changes = new AuditChanges();
      FacilityUse use = work.apply(changes);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      details.put("useId", use.id().value().toString());
      details.put("label", use.name());
      changes.into(details);
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return use;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      if (useId != null) {
        details.put("useId", useId.value().toString());
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
