// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.MunicipalityPermission;
import gal.furar.municipality.application.port.in.SportsServiceSettingsUseCase;
import gal.furar.municipality.application.port.in.UpdatePaymentNotesCommand;
import gal.furar.municipality.application.port.in.UpdateSportsContactCommand;
import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.PaymentNotes;
import gal.furar.municipality.domain.PaymentNotesScope;
import gal.furar.municipality.domain.SportsContact;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenancyError;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contacto do servizo de deportes e notas de pago e devolución (D2-02). */
@Service
public class SportsServiceSettingsService implements SportsServiceSettingsUseCase {

  private final MunicipalityRepository repository;
  private final AuditLog audit;
  private final Clock clock;

  public SportsServiceSettingsService(
      MunicipalityRepository repository, AuditLog audit, Clock clock) {
    this.repository = repository;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public Municipality sportsContact() {
    return current();
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updateSportsContact(UpdateSportsContactCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.SPORTS_CONTACT,
        () -> {
          Municipality current = current(command.expectedVersion());
          SportsContact contact =
              new SportsContact(
                  command.name(), command.address(), command.phone(), command.email());
          return repository.update(current.withSportsContact(contact, clock.instant()));
        });
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public Municipality paymentNotes() {
    return current();
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updatePaymentNotes(UpdatePaymentNotesCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.PAYMENT_NOTES,
        () -> {
          Municipality current = current(command.expectedVersion());
          PaymentNotesScope scope = scope(command.scope(), current.paymentNotes().scope());
          PaymentNotes notes =
              PaymentNotes.of(
                  scope,
                  command.paymentGl(),
                  command.paymentEs(),
                  command.refundGl(),
                  command.refundEs());
          return repository.update(current.withPaymentNotes(notes, clock.instant()));
        });
  }

  private static PaymentNotesScope scope(@Nullable String name, PaymentNotesScope current) {
    if (name == null || name.isBlank()) {
      return current;
    }
    return PaymentNotesScope.fromName(name)
        .orElseThrow(
            () ->
                DomainException.validation(
                    List.of(FieldViolation.of("paymentNotes.scope", ValidationError.PATTERN))));
  }

  private Municipality current(long expectedVersion) {
    Municipality current = current();
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  private Municipality current() {
    return repository
        .findById(new MunicipalityId(TenantContext.require().id()))
        .orElseThrow(() -> new DomainException(TenancyError.MUNICIPALITY_NOT_FOUND));
  }
}
