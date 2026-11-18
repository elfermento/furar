// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Base de todo adaptador de persistencia dunha entidade con {@code @TenantId} (regra A7b). Cada
 * operación chama a {@link #tenant()}, que falla con {@link MissingTenantException} se non hai
 * concello ou se non hai transacción: sen transacción non hai {@code set_config} e a RLS devolvería
 * 0 filas en silencio (ADR-0006).
 */
public abstract class TenantScopedPersistenceAdapter {

  /** Concello actual; lanza {@link MissingTenantException} se non hai ou se non hai transacción. */
  protected final TenantRef tenant() {
    TenantRef tenant = TenantContext.require();
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw MissingTenantException.withoutTransaction();
    }
    return tenant;
  }
}
