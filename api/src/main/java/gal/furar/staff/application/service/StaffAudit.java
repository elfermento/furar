// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rexistro de actividade das accións do persoal (ADR-0020): o éxito na transacción da escritura e o
 * fallo ao momento, co código. Os detalles enchéos o traballo segundo avanza (pertenza, rol) e
 * nunca levan correo nin token.
 */
final class StaffAudit {

  private StaffAudit() {}

  static <T> T audited(
      AuditLog audit, String action, Map<String, Object> details, Supplier<T> work) {
    TenantRef tenant = TenantContext.require();
    try {
      T result = work.get();
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return result;
    } catch (RuntimeException e) {
      Map<String, Object> failure = new LinkedHashMap<>(details);
      failure.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(action, Outcome.FAILURE, tenant.id(), tenant.slug(), failure));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }
}
