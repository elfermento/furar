// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.security.RolePermissions;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;

/**
 * Comproba o {@link RequiresPermission} do caso de uso no concello do {@code TenantContext}, coa
 * matriz {@code RolePermissions} (ADR-0007).
 */
final class PermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

  private final CurrentActor currentActor;

  PermissionAuthorizationManager(CurrentActor currentActor) {
    this.currentActor = currentActor;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
    Optional<PermissionKey> registered = RolePermissions.byCode(codeOf(invocation));
    if (registered.isEmpty()) {
      // Pechado por defecto: un código sen rexistrar (gralla) nunca dá acceso.
      return new AuthorizationDecision(false);
    }
    PermissionKey permission = registered.get();
    Optional<Actor> actor = currentActor.current();
    String slug = TenantContext.current().map(TenantRef::slug).orElse(null);
    boolean granted = actor.isPresent() && actor.get().can(permission, slug);
    return new AuthorizationDecision(granted);
  }

  private static String codeOf(MethodInvocation invocation) {
    Method method = invocation.getMethod();
    Object target = invocation.getThis();
    if (target != null) {
      method = AopUtils.getMostSpecificMethod(method, AopUtils.getTargetClass(target));
    }
    RequiresPermission annotation =
        AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
    if (annotation == null) {
      throw new IllegalStateException("Missing @RequiresPermission on " + method);
    }
    return annotation.value();
  }
}
