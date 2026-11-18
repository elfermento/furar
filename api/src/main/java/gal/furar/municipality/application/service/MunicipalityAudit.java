// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.audit.AuditChanges;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * As escrituras do concello actual no rexistro de actividade (ADR-0020): o éxito na mesma
 * transacción, co que cambiou ({@link AuditChanges}); o fallo co código do erro.
 */
final class MunicipalityAudit {

  static final String CREATE = "municipality.create";
  static final String UPDATE = "municipality.update";
  static final String LEGAL_TEXTS = "municipality.legal-texts";
  static final String SPORTS_CONTACT = "municipality.sports-contact";
  static final String PAYMENT_NOTES = "municipality.payment-notes";
  static final String NO_SHOW_POLICY = "municipality.no-show-policy";
  static final String OPEN_DATA = "municipality.open-data";
  static final String OPEN_MATCH_POLICY = "municipality.open-match-policy";
  static final String INSTALL_HINT = "municipality.install-hint";

  private MunicipalityAudit() {}

  static <T> T audited(AuditLog audit, String action, Function<AuditChanges, T> work) {
    TenantRef tenant = TenantContext.require();
    AuditChanges changes = new AuditChanges();
    T result;
    try {
      result = work.apply(changes);
    } catch (RuntimeException e) {
      audit.recordFailure(e, action, tenant.id(), tenant.slug(), Map.of());
      throw e;
    }
    audit.record(
        new AuditEvent(
            action,
            Outcome.SUCCESS,
            tenant.id(),
            tenant.slug(),
            changes.into(new LinkedHashMap<>())));
    return result;
  }

  /** Compara o concello antes e despois da escritura e devolve o gardado. */
  static Municipality diff(AuditChanges changes, Municipality before, Municipality after) {
    changes.compare(MunicipalityAuditSnapshots.of(before), MunicipalityAuditSnapshots.of(after));
    return after;
  }
}
