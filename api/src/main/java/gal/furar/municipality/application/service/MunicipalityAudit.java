// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.Map;
import java.util.function.Supplier;

/**
 * As escrituras do concello actual no rexistro de actividade (ADR-0020): o éxito na mesma
 * transacción, o fallo co código do erro. Sen detalles: o concello xa é o obxecto da acción.
 */
final class MunicipalityAudit {

  static final String CREATE = "municipality.create";
  static final String UPDATE = "municipality.update";
  static final String LEGAL_TEXTS = "municipality.legal-texts";
  static final String SPORTS_CONTACT = "municipality.sports-contact";
  static final String PAYMENT_NOTES = "municipality.payment-notes";

  private MunicipalityAudit() {}

  static <T> T audited(AuditLog audit, String action, Supplier<T> work) {
    TenantRef tenant = TenantContext.require();
    T result;
    try {
      result = work.get();
    } catch (RuntimeException e) {
      audit.recordFailure(e, action, tenant.id(), tenant.slug(), Map.of());
      throw e;
    }
    audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), Map.of()));
    return result;
  }
}
