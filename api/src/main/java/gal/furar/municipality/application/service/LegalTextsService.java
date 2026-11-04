// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.MunicipalityPermission;
import gal.furar.municipality.application.port.in.LegalTextsUseCase;
import gal.furar.municipality.application.port.in.UpdateLegalTextsCommand;
import gal.furar.municipality.application.port.out.LegalHtmlSanitizer;
import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenancyError;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Textos propios das páxinas legais (ADR-0017 punto 8). O HTML sanéase aquí, no servidor, antes de
 * gardalo: a web pinta o que garda a base sen confiar no navegador de quen o escribiu.
 */
@Service
public class LegalTextsService implements LegalTextsUseCase {

  private final MunicipalityRepository repository;
  private final LegalHtmlSanitizer sanitizer;
  private final AuditLog audit;
  private final Clock clock;

  public LegalTextsService(
      MunicipalityRepository repository,
      LegalHtmlSanitizer sanitizer,
      AuditLog audit,
      Clock clock) {
    this.repository = repository;
    this.sanitizer = sanitizer;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public Municipality legalTexts() {
    return current();
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updateLegalTexts(UpdateLegalTextsCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.LEGAL_TEXTS,
        () -> {
          Municipality current = current();
          if (current.version() == null || current.version() != command.expectedVersion()) {
            throw new DomainException(CommonError.STALE_VERSION);
          }
          return repository.update(
              current.withLegalTexts(
                  MunicipalityInput.legalTexts(command.texts(), sanitizer), clock.instant()));
        });
  }

  private Municipality current() {
    return repository
        .findById(new MunicipalityId(TenantContext.require().id()))
        .orElseThrow(() -> new DomainException(TenancyError.MUNICIPALITY_NOT_FOUND));
  }
}
