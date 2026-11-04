// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

/**
 * Abre o concello do evento ({@link TenantAware}) arredor dun escoitador, antes de que comece a súa
 * transacción: Hibernate fixa o concello ao abrir a sesión (ADR-0006).
 */
public final class TenantListenerInterceptor implements MethodInterceptor {

  @Override
  public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
    for (Object argument : invocation.getArguments()) {
      if (argument instanceof TenantAware event) {
        try (TenantContext.Scope ignored =
            TenantContext.open(TenantRef.of(event.municipalityId()))) {
          return invocation.proceed();
        }
      }
    }
    return invocation.proceed();
  }
}
