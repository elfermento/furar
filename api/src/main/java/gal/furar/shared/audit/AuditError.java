// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros da lectura e da exportación de bloqueados do rexistro de actividade (plan do dominio 4,
 * §2.3; ADR-0020, revisión do 2026-10-29).
 */
public enum AuditError implements ErrorCode {
  /** O período non comeza antes de rematar ou dura máis de {@value AuditFilter#MAX_DAYS} días. */
  RANGE_INVALID("audit.range-invalid", 400),
  /** A referencia da exportación de bloqueados falta, é longa de máis ou ten caracteres raros. */
  EXPORT_REFERENCE_INVALID("audit.export-reference-invalid", 400),
  /** O período pedido non ten ningún tramo bloqueado (todo se ve na web ou xa se borrou). */
  EXPORT_NOT_BLOCKED("audit.export-not-blocked", 400),
  /** O período ten máis eventos dos que se exportan dunha vez. */
  EXPORT_TOO_LARGE("audit.export-too-large", 400);

  private final String code;
  private final int status;

  AuditError(String code, int status) {
    this.code = code;
    this.status = status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return status;
  }
}
