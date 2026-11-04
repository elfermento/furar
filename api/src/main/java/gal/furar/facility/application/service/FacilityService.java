// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityCreated;
import gal.furar.facility.FacilityPermission;
import gal.furar.facility.NoBookingImpact;
import gal.furar.facility.application.port.in.AddressInput;
import gal.furar.facility.application.port.in.ChangeFacilityStatusCommand;
import gal.furar.facility.application.port.in.ChangeFacilityStatusUseCase;
import gal.furar.facility.application.port.in.CreateFacilityCommand;
import gal.furar.facility.application.port.in.CreateFacilityUseCase;
import gal.furar.facility.application.port.in.DeleteFacilityCommand;
import gal.furar.facility.application.port.in.DeleteFacilityUseCase;
import gal.furar.facility.application.port.in.DescriptionInput;
import gal.furar.facility.application.port.in.DetailsInput;
import gal.furar.facility.application.port.in.FacilityGallery;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.GetFacilityUseCase;
import gal.furar.facility.application.port.in.ListFacilitiesUseCase;
import gal.furar.facility.application.port.in.PublishFacilityCommand;
import gal.furar.facility.application.port.in.PublishFacilityUseCase;
import gal.furar.facility.application.port.in.RenameFacilityCommand;
import gal.furar.facility.application.port.in.RenameFacilityUseCase;
import gal.furar.facility.application.port.in.SportRefInput;
import gal.furar.facility.application.port.in.UpdateFacilityAddressCommand;
import gal.furar.facility.application.port.in.UpdateFacilityAmenitiesCommand;
import gal.furar.facility.application.port.in.UpdateFacilityDescriptionCommand;
import gal.furar.facility.application.port.in.UpdateFacilityDetailsCommand;
import gal.furar.facility.application.port.in.UpdateFacilityPaymentNotesCommand;
import gal.furar.facility.application.port.in.UpdateFacilitySectionUseCase;
import gal.furar.facility.application.port.in.UpdateHouseRulesCommand;
import gal.furar.facility.application.port.out.FacilityCustomFields;
import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRemoval;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDescription;
import gal.furar.facility.domain.FacilityDetails;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityPaymentNotes;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.HouseRules;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso da instalación. Cada método público declara o seu permiso (A15) e cada escritura
 * queda no rexistro de actividade, boa ou fallida (ADR-0020, ADR-0022 punto 14). O cambio de estado
 * toma primeiro o bloqueo da instalación (punto 4) e pasa polo modal de reservas afectadas (punto
 * 6).
 */
@Service
public class FacilityService
    implements CreateFacilityUseCase,
        GetFacilityUseCase,
        ListFacilitiesUseCase,
        RenameFacilityUseCase,
        ChangeFacilityStatusUseCase,
        UpdateFacilitySectionUseCase,
        PublishFacilityUseCase,
        DeleteFacilityUseCase {

  public static final int MAX_PAGE_SIZE = 100;
  static final String CREATE = "facility.create";
  static final String RENAME = "facility.rename";
  static final String STATUS = "facility.status";
  static final String DETAILS = "facility.details";
  static final String DESCRIPTION = "facility.description";
  static final String HOUSE_RULES = "facility.house-rules";
  static final String PAYMENT_NOTES = "facility.payment-notes";
  static final String ADDRESS = "facility.address";
  static final String AMENITIES = "facility.amenities";
  static final String DELETE = "facility.delete";
  static final String PUBLISH = "facility.publish";

  private final FacilityRepository repository;
  private final FacilityLock lock;
  private final FacilityRemoval removal;
  private final BookingImpact bookings;
  private final AffectedBookingsGate gate;
  private final FacilityDrafts drafts;
  private final SportRepository sports;
  private final FacilityGalleries galleries;
  private final FacilityCustomFields customFields;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Un só {@link BookingImpact} (ADR-0005): sen ningún, {@link NoBookingImpact}; con dous, non
   * arrinca. Os {@link RequirementProbe} poden faltar (o requisito falta sempre).
   */
  @Autowired
  public FacilityService(
      FacilityRepository repository,
      FacilityLock lock,
      FacilityRemoval removal,
      ObjectProvider<BookingImpact> bookingImpact,
      ObjectProvider<RequirementProbe> probes,
      SportRepository sports,
      FacilityGalleries galleries,
      FacilityCustomFields customFields,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this(
        repository,
        lock,
        removal,
        bookingImpact.getIfAvailable(NoBookingImpact::new),
        probes.orderedStream().toList(),
        sports,
        galleries,
        customFields,
        audit,
        events,
        clock);
  }

  public FacilityService(
      FacilityRepository repository,
      FacilityLock lock,
      FacilityRemoval removal,
      BookingImpact bookingImpact,
      List<RequirementProbe> probes,
      SportRepository sports,
      FacilityGalleries galleries,
      FacilityCustomFields customFields,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.lock = lock;
    this.removal = removal;
    this.bookings = bookingImpact;
    this.gate = new AffectedBookingsGate(bookingImpact);
    this.drafts = new FacilityDrafts(probes);
    this.sports = sports;
    this.galleries = galleries;
    this.customFields = customFields;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView create(CreateFacilityCommand command) {
    return audited(
        CREATE,
        null,
        () -> {
          Facility saved =
              repository.insert(
                  Facility.create(
                      FacilityId.newId(),
                      command.name(),
                      command.address() == null ? null : address(command.address()),
                      clock.instant()));
          events.publishEvent(
              new FacilityCreated(TenantContext.require().id(), saved.id().value()));
          return view(saved);
        });
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public FacilityView get(FacilityId id) {
    return view(load(id));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public PageResult<FacilityView> list(int page, int size, Language language) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("max", MAX_PAGE_SIZE));
    }
    PageResult<Facility> result = repository.findPage(page, size, language);
    Map<UUID, Set<Requirement>> missing =
        drafts.missing(result.items().stream().map(f -> f.id().value()).toList());
    Map<FacilityId, FacilityGallery> covers = galleries.of(result.items());
    Map<SportRef, Sport> named = SportNames.of(sports);
    return new PageResult<>(
        result.items().stream()
            .map(
                f ->
                    new FacilityView(
                        f,
                        missing.get(f.id().value()),
                        SportNames.resolve(named, f),
                        covers.get(f.id()),
                        CustomFieldValues.empty()))
            .toList(),
        result.page(),
        result.size(),
        result.totalItems());
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView rename(RenameFacilityCommand command) {
    return audited(
        RENAME,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          return view(repository.update(current.rename(command.name(), clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView changeStatus(ChangeFacilityStatusCommand command) {
    return audited(
        STATUS,
        command.id(),
        () -> {
          if (!lock.lock(command.id())) {
            throw new DomainException(FacilityError.NOT_FOUND);
          }
          Facility current = current(command.id(), command.expectedVersion());
          if (current.status() == command.status()) {
            return view(current);
          }
          FacilityChange change = changeFor(current.id(), command.status());
          List<BookingDecision> decisions =
              change == null ? List.of() : gate.check(change, command.decisions());
          Facility saved =
              repository.update(current.changeStatus(command.status(), clock.instant()));
          if (change != null) {
            gate.apply(change, decisions);
          }
          return view(saved);
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView updateDetails(UpdateFacilityDetailsCommand command) {
    return audited(
        DETAILS,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          FacilityDetails details = details(command.details());
          CustomFieldValues values = customFields.validate(current.id(), command.customFields());
          // Na mesma transacción: a instalación sobe de versión aínda que só cambien os campos
          // propios, e os valores substitúense (ADR-0011).
          Facility saved = repository.update(current.updateDetails(details, clock.instant()));
          customFields.replace(saved.id(), values);
          return view(saved);
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView updateDescription(UpdateFacilityDescriptionCommand command) {
    return audited(
        DESCRIPTION,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          return view(
              repository.update(
                  current.describe(description(command.description()), clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView updateHouseRules(UpdateHouseRulesCommand command) {
    return audited(
        HOUSE_RULES,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          HouseRules rules = HouseRules.of(command.houseRules().gl(), command.houseRules().es());
          return view(repository.update(current.withHouseRules(rules, clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView updatePaymentNotes(UpdateFacilityPaymentNotesCommand command) {
    return audited(
        PAYMENT_NOTES,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          FacilityPaymentNotes notes =
              FacilityPaymentNotes.of(
                  command.payment().gl(),
                  command.payment().es(),
                  command.refund().gl(),
                  command.refund().es());
          return view(repository.update(current.withPaymentNotes(notes, clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView updateAddress(UpdateFacilityAddressCommand command) {
    return audited(
        ADDRESS,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          return view(
              repository.update(current.relocate(address(command.address()), clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView updateAmenities(UpdateFacilityAmenitiesCommand command) {
    return audited(
        AMENITIES,
        command.id(),
        () -> {
          Facility current = current(command.id(), command.expectedVersion());
          return view(repository.update(current.equip(command.amenities(), clock.instant())));
        });
  }

  /**
   * «Publicar» baixo o bloqueo da instalación (cambia a súa reservabilidade, ADR-0022 punto 4): co
   * enderezo e sen nada que falte para saír de borrador.
   */
  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView publish(PublishFacilityCommand command) {
    return audited(
        PUBLISH,
        command.id(),
        () -> {
          if (!lock.lock(command.id())) {
            throw new DomainException(FacilityError.NOT_FOUND);
          }
          Facility current = current(command.id(), command.expectedVersion());
          if (current.published()) {
            return view(current);
          }
          if (!drafts.missing(current.id().value()).isEmpty()) {
            throw new DomainException(FacilityError.NOT_PUBLISHABLE);
          }
          return view(repository.update(current.publish(clock.instant())));
        });
  }

  /**
   * Borra a instalación baixo o seu bloqueo: só se nunca tivo reservas (se as tivo, dáse de baixa
   * para conservar o historial). As fotos seguen na biblioteca; os campos propios vanse con ela.
   */
  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public void delete(DeleteFacilityCommand command) {
    audited(
        DELETE,
        command.id(),
        () -> {
          if (!lock.lock(command.id())) {
            throw new DomainException(FacilityError.NOT_FOUND);
          }
          Facility current = current(command.id(), command.expectedVersion());
          if (bookings.hasBookings(current.id().value())) {
            throw new DomainException(FacilityError.HAS_BOOKINGS);
          }
          FacilityView gone = new FacilityView(current, Set.of());
          customFields.removeAll(current.id());
          removal.remove(current.id());
          return gone;
        });
  }

  /** Pechar e dar de baixa poden deixar reservas fóra; volver a activa, non. */
  private static @Nullable FacilityChange changeFor(FacilityId id, FacilityStatus status) {
    return switch (status) {
      case TEMPORARILY_CLOSED -> new FacilityChange.Closure(id.value());
      case WITHDRAWN -> new FacilityChange.Withdrawal(id.value());
      case ACTIVE -> null;
    };
  }

  private static Address address(@Nullable AddressInput input) {
    if (input == null) {
      throw new DomainException(FacilityError.ADDRESS_REQUIRED);
    }
    return new Address(
        input.street(),
        input.streetNumber(),
        input.postalCode(),
        input.locality(),
        input.ineCode(),
        input.latitude(),
        input.longitude());
  }

  /** Tarxeta Datos: cada deporte co código do catálogo ou o id do concello, nunca os dous. */
  static FacilityDetails details(DetailsInput input) {
    List<SportRef> refs = new ArrayList<>();
    List<FieldViolation> invalid = new ArrayList<>();
    for (int i = 0; i < input.sports().size(); i++) {
      SportRefInput sport = input.sports().get(i);
      if (sport == null || (sport.code() == null) == (sport.customSportId() == null)) {
        invalid.add(FieldViolation.of("details.sports[" + i + "]", ValidationError.INVALID));
      } else {
        refs.add(
            sport.code() != null
                ? SportRef.catalog(sport.code())
                : SportRef.custom(sport.customSportId()));
      }
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(FacilityError.DETAILS_INVALID, Map.of(), invalid);
    }
    return new FacilityDetails(
        refs,
        input.type(),
        input.ridegaCode(),
        input.phone(),
        input.email(),
        input.onlineBooking(),
        input.showFreeSlots(),
        input.covered());
  }

  static @Nullable FacilityDescription description(DescriptionInput input) {
    return FacilityDescription.of(input.gl(), input.es());
  }

  private Facility current(FacilityId id, long expectedVersion) {
    Facility current = load(id);
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  private Facility load(FacilityId id) {
    return repository.findById(id).orElseThrow(() -> new DomainException(FacilityError.NOT_FOUND));
  }

  /** A ficha: co que falta, cos deportes co seu nome, coas imaxes e cos campos propios visibles. */
  private FacilityView view(Facility facility) {
    return new FacilityView(
        facility,
        drafts.missing(facility.id().value()),
        sports.findAll(facility.details().sports()),
        galleries.of(facility),
        customFields.visible(facility.id()),
        bookings.hasBookings(facility.id().value()));
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito co id e o estado; o fallo co código. Se o
   * rexistro do fallo non se pode escribir, mantense o erro orixinal.
   */
  private FacilityView audited(
      String action, @Nullable FacilityId id, Supplier<FacilityView> work) {
    TenantRef tenant = TenantContext.require();
    try {
      FacilityView view = work.get();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", view.value().id().value().toString());
      details.put("status", view.value().status().name());
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return view;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      if (id != null) {
        details.put("facilityId", id.value().toString());
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
