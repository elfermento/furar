// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.MunicipalityPermission;
import gal.furar.municipality.application.port.in.SportsServiceSettingsUseCase;
import gal.furar.municipality.application.port.in.UpdateInstallHintCommand;
import gal.furar.municipality.application.port.in.UpdateNoShowPolicyCommand;
import gal.furar.municipality.application.port.in.UpdateOpenDataCommand;
import gal.furar.municipality.application.port.in.UpdateOpenMatchPolicyCommand;
import gal.furar.municipality.application.port.in.UpdatePaymentNotesCommand;
import gal.furar.municipality.application.port.in.UpdateSportsContactCommand;
import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.NoShowPolicy;
import gal.furar.municipality.domain.OpenMatchPolicy;
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
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contacto do servizo de deportes e notas de pago e devolución. */
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
        changes -> {
          Municipality current = current(command.expectedVersion());
          SportsContact contact =
              new SportsContact(
                  command.name(), command.address(), command.phone(), command.email());
          return MunicipalityAudit.diff(
              changes,
              current,
              repository.update(current.withSportsContact(contact, clock.instant())));
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
        changes -> {
          Municipality current = current(command.expectedVersion());
          PaymentNotesScope scope = scope(command.scope(), current.paymentNotes().scope());
          PaymentNotes notes =
              PaymentNotes.of(
                  scope,
                  command.paymentGl(),
                  command.paymentEs(),
                  command.refundGl(),
                  command.refundEs());
          return MunicipalityAudit.diff(
              changes,
              current,
              repository.update(current.withPaymentNotes(notes, clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updateNoShowPolicy(UpdateNoShowPolicyCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.NO_SHOW_POLICY,
        changes -> {
          Municipality current = current(command.expectedVersion());
          Instant now = clock.instant();
          NoShowPolicy policy =
              current
                  .noShowPolicy()
                  .changed(
                      command.enabled(),
                      command.threshold(),
                      command.windowDays(),
                      command.blockDays(),
                      now);
          return MunicipalityAudit.diff(
              changes, current, repository.update(current.withNoShowPolicy(policy, now)));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updateOpenMatchPolicy(UpdateOpenMatchPolicyCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.OPEN_MATCH_POLICY,
        changes -> {
          Municipality current = current(command.expectedVersion());
          Instant now = clock.instant();
          OpenMatchPolicy policy =
              current.openMatchPolicy().changed(command.enabled(), command.cancelHours(), now);
          return MunicipalityAudit.diff(
              changes, current, repository.update(current.withOpenMatchPolicy(policy, now)));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updateOpenData(UpdateOpenDataCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.OPEN_DATA,
        changes -> {
          Municipality current = current(command.expectedVersion());
          return MunicipalityAudit.diff(
              changes,
              current,
              repository.update(current.withOpenData(command.enabled(), clock.instant())));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(MunicipalityPermission.Code.UPDATE)
  public Municipality updateInstallHint(UpdateInstallHintCommand command) {
    return MunicipalityAudit.audited(
        audit,
        MunicipalityAudit.INSTALL_HINT,
        changes -> {
          Municipality current = current(command.expectedVersion());
          return MunicipalityAudit.diff(
              changes,
              current,
              repository.update(current.withInstallHint(command.enabled(), clock.instant())));
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
