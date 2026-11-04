// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comproba un {@link PersonUseCase} antes de entrar (ADR-0025, punto 3): sen persoa, {@code 401
 * auth.unauthenticated}; co correo sen verificar, {@code 403 auth.email-not-verified}. Os endpoints
 * xa piden token (ADR-0007); isto é a segunda capa, no caso de uso, como {@code
 * RequiresPermission}.
 */
final class PersonUseCaseInterceptor implements MethodInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(PersonUseCaseInterceptor.class);

  private final CurrentActor currentActor;

  PersonUseCaseInterceptor(CurrentActor currentActor) {
    this.currentActor = currentActor;
  }

  @Override
  public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    if (!actor.emailVerified()) {
      LOG.info("Person use case without verified email: subject={}", actor.subject());
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    return invocation.proceed();
  }
}
