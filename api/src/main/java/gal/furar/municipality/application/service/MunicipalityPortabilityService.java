// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.customfields.CustomFieldValidator;
import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.municipality.MunicipalityCreated;
import gal.furar.municipality.application.port.in.ExportMunicipalityUseCase;
import gal.furar.municipality.application.port.in.ImportMunicipalityCommand;
import gal.furar.municipality.application.port.in.ImportMunicipalityUseCase;
import gal.furar.municipality.application.port.in.MunicipalityExport;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.out.LegalHtmlSanitizer;
import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.Slug;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LanguagePolicy;
import gal.furar.shared.portability.MunicipalityDataSection;
import gal.furar.shared.portability.PortabilityError;
import gal.furar.shared.portability.PortabilityPermission;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenancyError;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Portabilidade dos datos dun concello (ADR-0018): exportación e importación no formato {@code
 * furar-export}. Cada módulo achega a súa sección ({@link MunicipalityDataSection}); este servizo
 * só coñece o concello e a orde. Toda exportación e importación, tamén as fallidas, queda no
 * rexistro de actividade ({@link AuditLog}, ADR-0020).
 */
@Service
public class MunicipalityPortabilityService
    implements ExportMunicipalityUseCase, ImportMunicipalityUseCase {

  private final MunicipalityRepository repository;
  private final List<MunicipalityDataSection<?>> sections;
  private final CustomFieldValidator customFieldValidator;
  private final CustomFieldValueStore customFieldStore;
  private final LanguagePolicy languagePolicy;
  private final LegalHtmlSanitizer sanitizer;
  private final TransactionTemplate transaction;
  private final ApplicationEventPublisher events;
  private final AuditLog audit;
  private final Clock clock;

  public MunicipalityPortabilityService(
      MunicipalityRepository repository,
      List<MunicipalityDataSection<?>> sections,
      CustomFieldValidator customFieldValidator,
      CustomFieldValueStore customFieldStore,
      LanguagePolicy languagePolicy,
      LegalHtmlSanitizer sanitizer,
      TransactionTemplate transaction,
      ApplicationEventPublisher events,
      AuditLog audit,
      Clock clock) {
    this.repository = repository;
    this.sections = List.copyOf(sections);
    this.customFieldValidator = customFieldValidator;
    this.customFieldStore = customFieldStore;
    this.languagePolicy = languagePolicy;
    this.sanitizer = sanitizer;
    this.transaction = transaction;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(PortabilityPermission.Code.EXPORT)
  public MunicipalityExport export() {
    TenantRef tenant = TenantContext.require();
    try {
      Municipality municipality =
          repository
              .findById(new MunicipalityId(tenant.id()))
              .orElseThrow(() -> new DomainException(TenancyError.MUNICIPALITY_NOT_FOUND));
      Map<String, List<?>> rows = new LinkedHashMap<>();
      sections.forEach(section -> rows.put(section.key(), section.exportRows()));
      // Antes de devolver os datos: sen rexistro non hai exportación.
      audit.record(event(EXPORT, Outcome.SUCCESS, tenant.id(), tenant.slug(), counts(rows)));
      CustomFieldValues customFields =
          customFieldStore.load(CustomFieldTarget.MUNICIPALITY, municipality.id().value());
      return new MunicipalityExport(municipality, customFields, rows, clock.instant());
    } catch (RuntimeException e) {
      recordFailure(EXPORT, tenant.id(), tenant.slug(), e);
      throw e;
    }
  }

  /**
   * Crea o concello e carga as seccións nunha transacción, xa dentro do concello novo: Hibernate
   * fixa o concello ao abrir a sesión, así que o {@code TenantContext} ábrese antes ca ela.
   */
  @Override
  @RequiresPermission(PortabilityPermission.Code.IMPORT)
  public MunicipalityView importMunicipality(ImportMunicipalityCommand command) {
    try {
      MunicipalityView view = importFile(command);
      audit.record(
          event(
              IMPORT,
              Outcome.SUCCESS,
              command.id(),
              view.municipality().slug().value(),
              counts(command.sections())));
      return view;
    } catch (RuntimeException e) {
      recordFailure(IMPORT, command.id(), command.slug(), e);
      throw e;
    }
  }

  private MunicipalityView importFile(ImportMunicipalityCommand command) {
    Slug slug = new Slug(command.slug());
    Set<String> known = new TreeSet<>();
    sections.forEach(section -> known.add(section.key()));
    Set<String> unknown = new TreeSet<>(command.sections().keySet());
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new DomainException(
          PortabilityError.INVALID_FILE,
          Map.of("reason", "unknown-section", "section", String.join(",", unknown)));
    }
    return TenantContext.runAs(
        new TenantRef(command.id(), slug.value()),
        () -> transaction.execute(status -> load(command, slug)));
  }

  static final String EXPORT = "municipality.export";
  static final String IMPORT = "municipality.import";

  private static AuditEvent event(
      String action,
      Outcome outcome,
      @Nullable UUID id,
      @Nullable String slug,
      Map<String, Object> details) {
    return new AuditEvent(action, outcome, id, slug, details);
  }

  /** Filas por sección: canto saíu ou entrou, sen os datos. */
  private static Map<String, Object> counts(Map<String, List<?>> rows) {
    Map<String, Object> counts = new LinkedHashMap<>();
    rows.forEach((key, list) -> counts.put(key, list == null ? 0 : list.size()));
    return Map.of("rows", counts);
  }

  /** O fallo tamén queda; se nin iso se pode escribir, mantense o erro orixinal. */
  private void recordFailure(
      String action, @Nullable UUID id, @Nullable String slug, RuntimeException e) {
    String code = e instanceof DomainException d ? d.errorCode().code() : "common.internal";
    try {
      audit.record(event(action, Outcome.FAILURE, id, slug, Map.of("code", code)));
    } catch (RuntimeException auditFailure) {
      e.addSuppressed(auditFailure);
    }
  }

  private MunicipalityView load(ImportMunicipalityCommand command, Slug slug) {
    MunicipalityId id = new MunicipalityId(command.id());
    if (repository.existsBySlug(slug) || repository.findById(id).isPresent()) {
      throw new DomainException(PortabilityError.CONFLICT, Map.of("slug", slug.value()));
    }
    Municipality saved =
        repository.insert(
            Municipality.restore(
                    id,
                    slug,
                    command.name(),
                    MunicipalityInput.languages(command.languages(), languagePolicy),
                    MunicipalityInput.language(command.defaultLanguage()),
                    MunicipalityInput.theme(command.theme()),
                    command.createdAt(),
                    command.updatedAt())
                .withLegal(MunicipalityInput.legal(command.legal()))
                .withLegalTexts(MunicipalityInput.legalTexts(command.legalTexts(), sanitizer)));
    for (MunicipalityDataSection<?> section : sections) {
      importSection(section, command.sections().getOrDefault(section.key(), List.of()));
    }
    Map<String, Object> raw = command.customFields() == null ? Map.of() : command.customFields();
    CustomFieldValues values = CustomFieldValues.empty();
    if (!raw.isEmpty()) {
      // Sen tocar o concello: os valores importados non son unha edición.
      values = customFieldValidator.restore(CustomFieldTarget.MUNICIPALITY, raw);
      customFieldStore.replace(CustomFieldTarget.MUNICIPALITY, saved.id().value(), values);
    }
    events.publishEvent(new MunicipalityCreated(saved.id().value(), saved.slug().value()));
    return new MunicipalityView(saved, values);
  }

  /** As filas xa veñen co tipo da sección (o adaptador web convérteas con {@code rowType()}). */
  @SuppressWarnings("unchecked")
  private static <T> void importSection(MunicipalityDataSection<T> section, List<?> rows) {
    for (Object row : rows) {
      if (!section.rowType().isInstance(row)) {
        throw new DomainException(
            PortabilityError.INVALID_FILE, Map.of("reason", "row", "section", section.key()));
      }
    }
    section.importRows((List<T>) rows);
  }
}
