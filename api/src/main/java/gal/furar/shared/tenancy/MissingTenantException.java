// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

/**
 * Acceso a datos dun concello sen concello no contexto ou sen transacción. É sempre un erro de
 * programación (ADR-0006): a petición non pasou por {@code TenantFilter}, a tarefa non abriu {@link
 * TenantContext#runAs} ou o caso de uso non é {@code @Transactional} (sen transacción non se fixa o
 * concello da RLS).
 */
public class MissingTenantException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public MissingTenantException() {
    super(
        "No municipality in TenantContext: use TenantContext.runAs(...) or a /municipalities/{slug} route");
  }

  private MissingTenantException(String message) {
    super(message);
  }

  /** Hai concello pero non transacción: a RLS non tería concello. */
  public static MissingTenantException withoutTransaction() {
    return new MissingTenantException(
        "No transaction: municipality data is only read or written inside @Transactional"
            + " (row level security is set per transaction)");
  }
}
