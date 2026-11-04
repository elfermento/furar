// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.FacilityPermission;
import gal.furar.facility.application.port.in.CreateSportCommand;
import gal.furar.facility.application.port.in.SportsUseCase;
import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.domain.CustomSport;
import gal.furar.facility.domain.CustomSportId;
import gal.furar.facility.domain.Sport;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.text.Collator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deportes (especificación, bloque 1): o catálogo de Furar e os que crea o concello con «+ crear».
 * Cada alta queda no rexistro de actividade, boa ou fallida (ADR-0020).
 */
@Service
public class SportService implements SportsUseCase {

  static final String CREATE = "facility.sport-create";

  private final SportRepository repository;
  private final AuditLog audit;
  private final Clock clock;

  public SportService(SportRepository repository, AuditLog audit, Clock clock) {
    this.repository = repository;
    this.audit = audit;
    this.clock = clock;
  }

  /** Poucos (~30 + os do concello): ordénanse aquí coa colación do idioma, sen índice. */
  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public List<Sport> list(Language language) {
    Collator collator = Collator.getInstance(Locale.of(language.code(), "ES"));
    collator.setStrength(Collator.SECONDARY);
    List<Sport> all = new ArrayList<>(repository.findAll());
    all.sort(Comparator.comparing((Sport s) -> s.label().in(language), collator));
    return all;
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public Sport create(CreateSportCommand command) {
    TenantRef tenant = TenantContext.require();
    try {
      CustomSport saved =
          repository.insert(
              CustomSport.create(
                  CustomSportId.newId(), command.gl(), command.es(), clock.instant()));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("customSportId", saved.id().value().toString());
      audit.record(new AuditEvent(CREATE, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return saved.sport();
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(CREATE, Outcome.FAILURE, tenant.id(), tenant.slug(), details));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }
}
