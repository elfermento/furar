// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.service;

import gal.furar.customfields.CustomFieldPermission;
import gal.furar.customfields.application.port.in.ChangeCustomFieldStatusUseCase;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.in.CustomFieldInUse;
import gal.furar.customfields.application.port.in.GetCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.in.GetCustomFieldInUseUseCase;
import gal.furar.customfields.application.port.in.GetCustomFieldSchemaUseCase;
import gal.furar.customfields.application.port.in.ListCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.CustomFieldError;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.JsonSchemaGenerator;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Audiences;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso das definicións de campos personalizados. Cada escritura queda no rexistro de
 * actividade (ADR-0020, ADR-0022 punto 14) co id, o código e o sitio do campo; nunca valores.
 */
@Service
public class CustomFieldDefinitionService
    implements CreateCustomFieldDefinitionUseCase,
        UpdateCustomFieldDefinitionUseCase,
        ChangeCustomFieldStatusUseCase,
        GetCustomFieldDefinitionUseCase,
        ListCustomFieldDefinitionsUseCase,
        GetCustomFieldSchemaUseCase,
        GetCustomFieldInUseUseCase {

  /** Máximo de campos activos usados nun destino dun concello. */
  public static final int MAX_ACTIVE_DEFINITIONS = 50;

  static final String CREATE = "customfield.create";
  static final String UPDATE = "customfield.update";
  static final String DEACTIVATE = "customfield.deactivate";
  static final String ACTIVATE = "customfield.activate";
  static final String DELETE = "customfield.delete";

  private final CustomFieldDefinitionRepository repository;
  private final CustomFieldTargets targets;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final Clock clock;

  public CustomFieldDefinitionService(
      CustomFieldDefinitionRepository repository,
      CustomFieldTargets targets,
      CurrentActor currentActor,
      AuditLog audit,
      Clock clock) {
    this.repository = repository;
    this.targets = targets;
    this.currentActor = currentActor;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public CustomFieldDefinition create(CreateCustomFieldDefinitionCommand command) {
    return audited(CREATE, null, () -> doCreate(command));
  }

  private CustomFieldDefinition doCreate(CreateCustomFieldDefinitionCommand command) {
    for (FieldUsage usage : command.spec().usages()) {
      checkTarget(usage.target(), command.code());
      checkVisibility(usage);
      checkLimit(usage.target());
    }
    if (repository.existsByCode(command.code())) {
      throw new DomainException(CustomFieldError.CODE_TAKEN, Map.of("code", command.code()));
    }
    CustomFieldDefinition definition =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            command.code(),
            command.type(),
            command.spec(),
            clock.instant());
    return repository.insert(definition);
  }

  /**
   * Un sitio novo pasa as mesmas comprobacións ca nunha alta e só se deixa cambiar mentres o campo
   * non ten valores ({@code customfield.target-has-values}). Unha opción quitada da lista bórrase
   * se non ten valores; se os ten, {@code customfield.option-has-values}: desactívase.
   */
  @Override
  @Transactional
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public CustomFieldDefinition update(UpdateCustomFieldDefinitionCommand command) {
    return audited(UPDATE, command.id(), () -> doUpdate(command));
  }

  private CustomFieldDefinition doUpdate(UpdateCustomFieldDefinitionCommand command) {
    CustomFieldDefinition current = load(command.id(), command.expectedVersion());
    CustomFieldDefinition updated = current.update(command.spec(), clock.instant());
    FieldUsage usage = command.spec().usages().getFirst();
    if (current.targetChanged(command.spec())) {
      checkTarget(usage.target(), current.code());
      if (repository.hasValues(current.id())) {
        throw new DomainException(
            CustomFieldError.TARGET_HAS_VALUES, Map.of("target", current.target().code()));
      }
      if (current.isActive()) {
        checkLimit(usage.target());
      }
    }
    checkVisibility(usage);
    Set<String> removed = current.removedOptions(command.spec());
    if (!removed.isEmpty()) {
      Set<String> used =
          repository.optionsWithValues(List.of(current.id())).getOrDefault(current.id(), Set.of());
      for (String option : removed) {
        if (used.contains(option)) {
          throw new DomainException(CustomFieldError.OPTION_HAS_VALUES, Map.of("option", option));
        }
      }
    }
    return repository.update(updated);
  }

  @Override
  @Transactional
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public CustomFieldDefinition deactivate(CustomFieldDefinitionId id, long expectedVersion) {
    return audited(
        DEACTIVATE,
        id,
        () -> {
          CustomFieldDefinition current = load(id, expectedVersion);
          CustomFieldDefinition inactive = current.deactivate(clock.instant());
          return inactive == current ? current : repository.update(inactive);
        });
  }

  @Override
  @Transactional
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public CustomFieldDefinition activate(CustomFieldDefinitionId id, long expectedVersion) {
    return audited(
        ACTIVATE,
        id,
        () -> {
          CustomFieldDefinition current = load(id, expectedVersion);
          if (current.isActive()) {
            return current;
          }
          current.targets().forEach(this::checkLimit);
          return repository.update(current.activate(clock.instant()));
        });
  }

  /**
   * Só sen valores: con eles, {@code customfield.has-values} e a web ofrece desactivalo. A base
   * tamén o impide (as FK dos valores ao campo e ás opcións).
   */
  @Override
  @Transactional
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public void delete(CustomFieldDefinitionId id, long expectedVersion) {
    audited(
        DELETE,
        id,
        () -> {
          CustomFieldDefinition current = load(id, expectedVersion);
          if (repository.hasValues(id)) {
            throw new DomainException(CustomFieldError.HAS_VALUES, Map.of("code", current.code()));
          }
          repository.delete(current);
          return current;
        });
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public Map<CustomFieldDefinitionId, CustomFieldInUse> inUse(
      List<CustomFieldDefinition> definitions) {
    if (definitions.isEmpty()) {
      return Map.of();
    }
    List<CustomFieldDefinitionId> ids =
        definitions.stream().map(CustomFieldDefinition::id).toList();
    Set<CustomFieldDefinitionId> withValues = repository.withValues(ids);
    Map<CustomFieldDefinitionId, Set<String>> options = repository.optionsWithValues(ids);
    Map<CustomFieldDefinitionId, CustomFieldInUse> result = new HashMap<>();
    for (CustomFieldDefinitionId id : ids) {
      result.put(
          id, new CustomFieldInUse(withValues.contains(id), options.getOrDefault(id, Set.of())));
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public CustomFieldDefinition get(CustomFieldDefinitionId id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new DomainException(CustomFieldError.DEFINITION_NOT_FOUND));
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public List<CustomFieldDefinition> listVisible(CustomFieldTarget target) {
    targets.require(target);
    Audience audience = currentAudience();
    // Quen vai reservar xa é o titular da súa reserva: ve os campos que vai encher.
    if (audience == Audience.PUBLIC
        && currentActor.current().isPresent()
        && targets.ownedByItsCreator(target)) {
      audience = Audience.OWNER;
    }
    Audience viewer = audience;
    // Tamén os desactivados: os formularios só pintan os activos, pero os valores gardados dun
    // desactivado séguense lendo coa súa etiqueta (revisión do 2026-11-04).
    return repository.findByTarget(target).stream()
        .filter(d -> d.usage(target).map(u -> u.visibility().visibleTo(viewer)).orElse(false))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public List<CustomFieldDefinition> listAll(CustomFieldTarget target) {
    targets.require(target);
    return repository.findByTarget(target);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(CustomFieldPermission.Code.MANAGE)
  public List<CustomFieldDefinition> listAll() {
    return repository.findAll();
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public Map<String, Object> schema(CustomFieldTarget target) {
    return JsonSchemaGenerator.generate(
        target, listVisible(target).stream().filter(CustomFieldDefinition::isActive).toList());
  }

  /** Destino rexistrado e código que non choca cun campo núcleo da entidade. */
  private void checkTarget(CustomFieldTarget target, String code) {
    if (targets.coreFields(target).contains(code)) {
      throw new DomainException(CustomFieldError.CODE_RESERVED, Map.of("code", code));
    }
  }

  /** Un destino que non admite campos públicos (a reserva) rexeita {@code PUBLIC}. */
  private void checkVisibility(FieldUsage usage) {
    if (usage.visibility() == Visibility.PUBLIC && !targets.allowsPublic(usage.target())) {
      throw new DomainException(
          CustomFieldError.VISIBILITY_NOT_ALLOWED,
          Map.of("target", usage.target().code(), "visibility", usage.visibility().name()));
    }
  }

  /** Máximo de campos activos usados nun destino. */
  private void checkLimit(CustomFieldTarget target) {
    long active =
        repository.findByTarget(target).stream().filter(CustomFieldDefinition::isActive).count();
    if (active >= MAX_ACTIVE_DEFINITIONS) {
      throw new DomainException(
          CustomFieldError.LIMIT_EXCEEDED, Map.of("limit", MAX_ACTIVE_DEFINITIONS));
    }
  }

  private CustomFieldDefinition load(CustomFieldDefinitionId id, long expectedVersion) {
    CustomFieldDefinition current =
        repository
            .findById(id)
            .orElseThrow(() -> new DomainException(CustomFieldError.DEFINITION_NOT_FOUND));
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(
          CommonError.STALE_VERSION, Map.of("currentVersion", String.valueOf(current.version())));
    }
    return current;
  }

  /**
   * Persoal se pode ver datos internos (tamén a plataforma, que configura o concello); titular do
   * concello se ten algún rol nel (non abonda ter sesión); se non, público.
   */
  private Audience currentAudience() {
    var actor = currentActor.current();
    String slug = TenantContext.current().map(TenantRef::slug).orElse(null);
    return Audiences.forConfiguration(actor, slug);
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito co id, o código e o sitio do campo; o fallo
   * co id (se o hai) e o código do erro. Se o rexistro do fallo non se pode escribir, mantense o
   * erro orixinal.
   */
  private CustomFieldDefinition audited(
      String action, @Nullable CustomFieldDefinitionId id, Supplier<CustomFieldDefinition> work) {
    TenantRef tenant = TenantContext.require();
    CustomFieldDefinition result;
    try {
      result = work.get();
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      if (id != null) {
        details.put("fieldId", id.value().toString());
      }
      audit.recordFailure(e, action, tenant.id(), tenant.slug(), details);
      throw e;
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("fieldId", result.id().value().toString());
    details.put("field", result.code());
    details.put("target", result.target().code());
    audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
    return result;
  }
}
