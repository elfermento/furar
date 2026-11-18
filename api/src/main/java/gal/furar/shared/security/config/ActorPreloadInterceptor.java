// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.NoPersonArrival;
import gal.furar.shared.security.NoVerifiedAccounts;
import gal.furar.shared.security.PersonArrival;
import gal.furar.shared.security.VerifiedAccounts;
import gal.furar.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Le quen fai a petición antes do controlador e fóra de toda transacción (ADR-0027 punto 2;
 * ADR-0028): con sesión, a administración da plataforma e, se hai concello, o rol da táboa de
 * persoal lense en transaccións curtas propias e quedan gardados na petición. Sen isto, un caso de
 * uso sen interceptor de permisos (os públicos, como ler o concello) preguntaba quen é dentro da
 * súa transacción e a lectura do rol collía unha segunda conexión mentres a primeira seguía
 * collida: con todo o pool así, as peticións quedaban agardando unha conexión que ningunha soltaba.
 *
 * <p>Na mesma lectura previa, e só co correo verificado (ADR-0031): a conta queda entre as
 * coñecidas polo seu correo ({@link VerifiedAccounts}, unha escritura por sesión) e, nas rutas da
 * persoa no concello ({@code …/municipalities/{slug}/me/…}), as reservas feitas no mostrador a nome
 * dese correo pasan á súa conta ({@link PersonArrival}). As dúas implementacións abren a súa propia
 * transacción curta.
 */
final class ActorPreloadInterceptor implements HandlerInterceptor {

  /** As rutas da persoa nun concello: as súas reservas. */
  private static final Pattern PERSON_ROUTE =
      Pattern.compile("^/api/v1/municipalities/[^/]+/me(/.*)?$");

  private final ObjectProvider<CurrentActor> actors;
  private final ObjectProvider<VerifiedAccounts> accounts;
  private final ObjectProvider<PersonArrival> arrivals;

  ActorPreloadInterceptor(
      ObjectProvider<CurrentActor> actors,
      ObjectProvider<VerifiedAccounts> accounts,
      ObjectProvider<PersonArrival> arrivals) {
    this.actors = actors;
    this.accounts = accounts;
    this.arrivals = arrivals;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal() instanceof Jwt jwt
        && !TransactionSynchronizationManager.isActualTransactionActive()) {
      CurrentActor actor = actors.getIfAvailable();
      if (actor != null) {
        actor.current();
      }
      String email = JwtCurrentActor.text(jwt, "email");
      if (email != null && JwtCurrentActor.emailVerified(jwt)) {
        accounts
            .getIfAvailable(NoVerifiedAccounts::new)
            .seen(jwt.getSubject(), email, JwtCurrentActor.authTime(jwt));
        if (PERSON_ROUTE.matcher(request.getRequestURI()).matches()
            && TenantContext.current().isPresent()) {
          arrivals.getIfAvailable(NoPersonArrival::new).arrived(jwt.getSubject(), email);
        }
      }
    }
    return true;
  }
}
