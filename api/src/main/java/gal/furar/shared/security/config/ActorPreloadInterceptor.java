// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Le quen fai a petición antes do controlador e fóra de toda transacción (R3, ADR-0027 punto 2;
 * ADR-0028): con sesión, a administración da plataforma e, se hai concello, o rol da táboa de
 * persoal lense en transaccións curtas propias e quedan gardados na petición. Sen isto, un caso de
 * uso sen interceptor de permisos (os públicos, como ler o concello) preguntaba quen é dentro da
 * súa transacción e a lectura do rol collía unha segunda conexión mentres a primeira seguía
 * collida: con todo o pool así, as peticións quedaban agardando unha conexión que ningunha soltaba.
 */
final class ActorPreloadInterceptor implements HandlerInterceptor {

  private final ObjectProvider<CurrentActor> actors;

  ActorPreloadInterceptor(ObjectProvider<CurrentActor> actors) {
    this.actors = actors;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal() instanceof Jwt
        && !TransactionSynchronizationManager.isActualTransactionActive()) {
      CurrentActor actor = actors.getIfAvailable();
      if (actor != null) {
        actor.current();
      }
    }
    return true;
  }
}
