// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.customfields.CustomFieldProjector;
import gal.furar.customfields.CustomFieldValidator;
import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.customfields.ValidationContext;
import gal.furar.municipality.MunicipalityCreated;
import gal.furar.municipality.MunicipalityPermission;
import gal.furar.municipality.application.port.in.CreateMunicipalityCommand;
import gal.furar.municipality.application.port.in.CreateMunicipalityUseCase;
import gal.furar.municipality.application.port.in.GetMunicipalityUseCase;
import gal.furar.municipality.application.port.in.ListMunicipalitiesUseCase;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.in.UpdateMunicipalityCommand;
import gal.furar.municipality.application.port.in.UpdateMunicipalityUseCase;
import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityError;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.Slug;
import gal.furar.municipality.domain.Theme;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LanguagePolicy;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.Audiences;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenancyError;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do concello. Dar de alta e cambiar os datos (xerais, aparencia, datos legais e
 * campos propios) quedan no rexistro de actividade (ADR-0020).
 */
@Service
public class MunicipalityService
    implements CreateMunicipalityUseCase,
        GetMunicipalityUseCase,
        ListMunicipalitiesUseCase,
        UpdateMunicipalityUseCase {

  public static final int MAX_PAGE_SIZE = 100;

  private final MunicipalityRepository repository;
  private final CustomFieldValidator customFieldValidator;
  private final CustomFieldProjector customFieldProjector;
  private final CustomFieldValueStore customFieldStore;
  private final CurrentActor currentActor;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final LanguagePolicy languagePolicy;
  private final AuditLog audit;

  public MunicipalityService(
      MunicipalityRepository repository,
      CustomFieldValidator customFieldValidator,
      CustomFieldProjector customFieldProjector,
      CustomFieldValueStore customFieldStore,
      CurrentActor currentActor,
      ApplicationEventPublisher events,
      Clock clock,
      LanguagePolicy languagePolicy,
      AuditLog audit) {
    this.repository = repository;
    this.customFieldValidator = customFieldValidator;
    this.customFieldProjector = customFieldProjector;
    this.customFieldStore = customFieldStore;
    this.currentActor = currentActor;
    this.events = events;
    this.clock = clock;
    this.languagePolicy = languagePolicy;
    this.audit = audit;
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.CREATE)
  public MunicipalityView create(CreateMunicipalityCommand command) {
    MunicipalityView view;
    try {
      view = doCreate(command);
    } catch (RuntimeException e) {
      // Sen concello aínda: o fallo vai ao rexistro da plataforma co slug pedido.
      audit.recordFailure(e, MunicipalityAudit.CREATE, null, command.slug(), Map.of());
      throw e;
    }
    Municipality saved = view.municipality();
    audit.record(
        new AuditEvent(
            MunicipalityAudit.CREATE,
            Outcome.SUCCESS,
            saved.id().value(),
            saved.slug().value(),
            Map.of()));
    return view;
  }

  private MunicipalityView doCreate(CreateMunicipalityCommand command) {
    Slug slug = new Slug(command.slug());
    if (repository.existsBySlug(slug)) {
      throw new DomainException(MunicipalityError.SLUG_TAKEN, Map.of("slug", slug.value()));
    }
    Municipality municipality =
        Municipality.create(
                MunicipalityId.newId(),
                slug,
                command.name(),
                MunicipalityInput.languages(command.languages(), languagePolicy),
                MunicipalityInput.language(command.defaultLanguage()),
                command.theme() == null ? Theme.DEFAULT : MunicipalityInput.theme(command.theme()),
                clock.instant())
            .withLegal(MunicipalityInput.legal(command.legal()));
    Municipality saved = repository.insert(municipality);
    events.publishEvent(new MunicipalityCreated(saved.id().value(), saved.slug().value()));
    return new MunicipalityView(saved, CustomFieldValues.empty());
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public MunicipalityView getCurrent() {
    Municipality municipality = loadCurrent();
    return new MunicipalityView(
        municipality,
        customFieldProjector.project(
            CustomFieldTarget.MUNICIPALITY, storedCustomFields(municipality), audience()));
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public PageResult<Municipality> list(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("max", MAX_PAGE_SIZE));
    }
    return repository.findPage(page, size);
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public MunicipalityView update(UpdateMunicipalityCommand command) {
    return MunicipalityAudit.audited(audit, MunicipalityAudit.UPDATE, () -> doUpdate(command));
  }

  private MunicipalityView doUpdate(UpdateMunicipalityCommand command) {
    Municipality current = loadCurrent();
    if (current.version() == null || current.version() != command.expectedVersion()) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Audience writer = audience();
    CustomFieldValues customFields =
        customFieldValidator.validate(
            CustomFieldTarget.MUNICIPALITY,
            command.customFields() == null ? Map.of() : command.customFields(),
            storedCustomFields(current),
            ValidationContext.of(writer));
    Municipality updated =
        current.update(
            command.name(),
            MunicipalityInput.languages(command.languages(), languagePolicy),
            MunicipalityInput.language(command.defaultLanguage()),
            MunicipalityInput.theme(command.theme()),
            command.legal() == null ? current.legal() : MunicipalityInput.legal(command.legal()),
            clock.instant());
    // Na mesma transacción: o concello sobe de versión e os valores substitúense (ADR-0011).
    Municipality saved = repository.update(updated);
    customFieldStore.replace(CustomFieldTarget.MUNICIPALITY, saved.id().value(), customFields);
    return new MunicipalityView(
        saved, customFieldProjector.project(CustomFieldTarget.MUNICIPALITY, customFields, writer));
  }

  private CustomFieldValues storedCustomFields(Municipality municipality) {
    return customFieldStore.load(CustomFieldTarget.MUNICIPALITY, municipality.id().value());
  }

  private Municipality loadCurrent() {
    TenantRef tenant = TenantContext.require();
    return repository
        .findById(new MunicipalityId(tenant.id()))
        .orElseThrow(() -> new DomainException(TenancyError.MUNICIPALITY_NOT_FOUND));
  }

  /**
   * Persoal se pode ver datos internos (tamén a plataforma, que configura o concello); titular do
   * concello se ten algún rol nel (non abonda ter sesión); se non, público.
   */
  private Audience audience() {
    var actor = currentActor.current();
    String slug = TenantContext.current().map(TenantRef::slug).orElse(null);
    return Audiences.forConfiguration(actor, slug);
  }
}
