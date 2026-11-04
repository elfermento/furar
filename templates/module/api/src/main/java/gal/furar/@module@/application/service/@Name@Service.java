// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.service;

import gal.furar.customfields.CustomFieldProjector; // @cf
import gal.furar.customfields.CustomFieldValidator; // @cf
import gal.furar.customfields.ValidationContext; // @cf
import gal.furar.@module@.@Name@Created;
import gal.furar.@module@.@Name@Permission;
import gal.furar.@module@.application.port.in.Create@Name@Command;
import gal.furar.@module@.application.port.in.Create@Name@UseCase;
import gal.furar.@module@.application.port.in.Export@Names@UseCase;
import gal.furar.@module@.application.port.in.Get@Name@UseCase;
import gal.furar.@module@.application.port.in.Import@Names@UseCase;
import gal.furar.@module@.application.port.in.List@Names@UseCase;
import gal.furar.@module@.application.port.in.Restore@Name@Command;
import gal.furar.@module@.application.port.in.Update@Name@Command;
import gal.furar.@module@.application.port.in.Update@Name@UseCase;
import gal.furar.@module@.application.port.in.@Name@View;
import gal.furar.@module@.application.port.out.@Name@Repository;
import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Error;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.portability.PortabilityPermission;
import gal.furar.shared.domain.Audience; // @cf
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget; // @cf
import gal.furar.shared.domain.CustomFieldValues; // @cf
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.Audiences; // @cf
import gal.furar.shared.security.CurrentActor; // @cf
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef; // @cf
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso. Cada método público declara o seu permiso (A15). Con campos personalizados:
 * valídaos aquí contra as definicións do concello (nunca só na web) e proxéctaos ao ler segundo
 * quen le (ADR-0011).
 */
@Service
public class @Name@Service
    implements Create@Name@UseCase,
        Get@Name@UseCase,
        List@Names@UseCase,
        Update@Name@UseCase,
        Export@Names@UseCase,
        Import@Names@UseCase {

  public static final int MAX_PAGE_SIZE = 100;
  static final CustomFieldTarget TARGET = @Name@.CUSTOM_FIELD_TARGET; // @cf

  private final @Name@Repository repository;
  private final CustomFieldValidator customFieldValidator; // @cf
  private final CustomFieldProjector customFieldProjector; // @cf
  private final CurrentActor currentActor; // @cf
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public @Name@Service(
      @Name@Repository repository,
      CustomFieldValidator customFieldValidator, // @cf
      CustomFieldProjector customFieldProjector, // @cf
      CurrentActor currentActor, // @cf
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.customFieldValidator = customFieldValidator; // @cf
    this.customFieldProjector = customFieldProjector; // @cf
    this.currentActor = currentActor; // @cf
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  @RequiresPermission(@Name@Permission.Code.MANAGE)
  public @Name@View create(Create@Name@Command command) {
    // @cf{
    Audience writer = audience();
    CustomFieldValues customFields =
        customFieldValidator.validate(
            TARGET, orEmpty(command.customFields()), CustomFieldValues.empty(), context(writer));
    // @cf}
    @Name@ saved =
        repository.insert(
            @Name@.create(
                @Name@Id.newId(),
                command.name(),
                customFields, // @cf
                clock.instant()));
    events.publishEvent(new @Name@Created(TenantContext.require().id(), saved.id().value()));
    return new @Name@View(
        customFieldProjector.project(TARGET, saved.customFields(), writer), // @cf
        saved);
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public @Name@View get(@Name@Id id) {
    @Name@ found = load(id);
    return new @Name@View(
        customFieldProjector.project(TARGET, found.customFields(), audience()), // @cf
        found);
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public PageResult<@Name@View> list(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("max", MAX_PAGE_SIZE));
    }
    PageResult<@Name@> result = repository.findPage(page, size);
    // @cf{
    // Unha lectura das definicións por páxina, non por fila (ARQ-6).
    List<CustomFieldValues> visible =
        customFieldProjector.projectAll(
            TARGET, result.items().stream().map(@Name@::customFields).toList(), audience());
    return new PageResult<>(
        java.util.stream.IntStream.range(0, result.items().size())
            .mapToObj(i -> new @Name@View(visible.get(i), result.items().get(i)))
            .toList(),
        result.page(),
        result.size(),
        result.totalItems());
    // @cf}
    // @nocf{
    return new PageResult<>(
        result.items().stream().map(@Name@View::new).toList(),
        result.page(),
        result.size(),
        result.totalItems());
    // @nocf}
  }

  @Override
  @Transactional
  @RequiresPermission(@Name@Permission.Code.MANAGE)
  public @Name@View update(Update@Name@Command command) {
    @Name@ current = load(command.id());
    if (current.version() == null || current.version() != command.expectedVersion()) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    // @cf{
    Audience writer = audience();
    CustomFieldValues customFields =
        customFieldValidator.validate(
            TARGET, orEmpty(command.customFields()), current.customFields(), context(writer));
    // @cf}
    @Name@ saved =
        repository.update(
            current.update(
                command.name(),
                customFields, // @cf
                clock.instant()));
    return new @Name@View(
        customFieldProjector.project(TARGET, saved.customFields(), writer), // @cf
        saved);
  }

  /** Portabilidade (ADR-0018): todos os valores, tamén os de persoal. */
  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(PortabilityPermission.Code.EXPORT)
  public List<@Name@> exportAll() {
    return repository.findAll();
  }

  /**
   * Portabilidade (ADR-0018): na transacción da importación do concello. Os valores
   * personalizados compróbanse contra as definicións xa importadas (sección customfields).
   */
  @Override
  @Transactional
  @RequiresPermission(PortabilityPermission.Code.IMPORT)
  public void importAll(List<Restore@Name@Command> rows) {
    for (Restore@Name@Command row : rows) {
      repository.insert(
          @Name@.restore(
              new @Name@Id(row.id()),
              row.name(),
              customFieldValidator.restore(TARGET, orEmpty(row.customFields())), // @cf
              row.createdAt(),
              row.updatedAt()));
    }
  }

  private @Name@ load(@Name@Id id) {
    return repository.findById(id).orElseThrow(() -> new DomainException(@Name@Error.NOT_FOUND));
  }
  // @cf{

  /**
   * Persoal se ten {@code STAFF_DATA_VIEW} no concello; se non, público. Se a entidade ten
   * titular (p. ex. unha reserva), pasa {@code isOwner} a {@link Audiences#of}.
   */
  private Audience audience() {
    String slug = TenantContext.current().map(TenantRef::slug).orElse(null);
    return Audiences.of(currentActor.current(), slug, false);
  }

  /** Atributos para {@code appliesWhen} das definicións (p. ex. tipo de espazo): engádeos aquí. */
  private static ValidationContext context(Audience writer) {
    return ValidationContext.of(writer);
  }

  private static Map<String, Object> orEmpty(Map<String, Object> raw) {
    return raw == null ? Map.of() : raw;
  }
  // @cf}
}
