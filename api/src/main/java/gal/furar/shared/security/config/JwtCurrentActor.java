// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.NoPlatformAdmins;
import gal.furar.shared.security.NoStaffRoles;
import gal.furar.shared.security.PlatformAdmins;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.security.RolePermissions;
import gal.furar.shared.security.StaffRoles;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Actor desde o JWT de Keycloak e as táboas de Furar (ADR-0007, ADR-0027, ADR-0028). Keycloak só
 * autentica: o token non dá ningún rol (o claim {@code furar_roles} xa non se le).
 *
 * <ul>
 *   <li>A administración da plataforma ({@code *:PLATFORM_ADMIN}) sae de {@link PlatformAdmins},
 *       unha vez por petición, con ou sen concello. Leva o correo do token só se está verificado:
 *       co de {@code FURAR_ADMIN_EMAIL} e sen ningunha administración, a conta pasa a selo
 *       (ADR-0028, punto 2).
 *   <li>O rol de persoal sae de {@link StaffRoles} no concello do {@code TenantContext}, unha vez
 *       por petición. Sen caché entre peticións: a baixa e o cambio de rol valen na seguinte.
 *   <li>Os dous resultados gárdanse en atributos da petición.
 *   <li>O {@code auth_time} pásaselle a {@link StaffRoles}: o «último acceso» rexístrase unha vez
 *       por sesión (ADR-0027, punto 5).
 * </ul>
 *
 * <p>{@code email_verified} di se a persoa pode usar os casos de uso de persoa ({@code
 * PersonUseCase}).
 */
@Component
class JwtCurrentActor implements CurrentActor {

  /** Prefixo do atributo da petición co rol da táboa, por concello. */
  static final String REQUEST_ATTRIBUTE = JwtCurrentActor.class.getName() + ".staffRole.";

  /** Atributo da petición coa administración da plataforma. */
  static final String PLATFORM_ATTRIBUTE = JwtCurrentActor.class.getName() + ".platformAdmin";

  /**
   * Fío que xa está lendo un rol: a implementación de {@link StaffRoles} rexistra {@code
   * staff.sign-in} (e a de {@link PlatformAdmins}, a primeira administración) e o rexistro de
   * actividade volve preguntar quen é a persoa. Esa volta non le as táboas outra vez (sería un
   * bucle): responde só co token.
   */
  private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);

  /**
   * Resoltos na primeira petición, non ao crear o bean: as implementacións usan o rexistro de
   * actividade, que á súa vez pregunta a este porto quen é a persoa.
   */
  private final Supplier<StaffRoles> staffRoles;

  private final Supplier<PlatformAdmins> platformAdmins;

  @Autowired
  JwtCurrentActor(
      ObjectProvider<StaffRoles> staffRoles, ObjectProvider<PlatformAdmins> platformAdmins) {
    this(
        SingletonSupplier.of(() -> staffRoles.getIfAvailable(NoStaffRoles::new)),
        SingletonSupplier.of(() -> platformAdmins.getIfAvailable(NoPlatformAdmins::new)));
  }

  JwtCurrentActor(StaffRoles staffRoles, PlatformAdmins platformAdmins) {
    this(() -> staffRoles, () -> platformAdmins);
  }

  private JwtCurrentActor(
      Supplier<StaffRoles> staffRoles, Supplier<PlatformAdmins> platformAdmins) {
    this.staffRoles = staffRoles;
    this.platformAdmins = platformAdmins;
  }

  @Override
  public Optional<Actor> current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    Set<RoleGrant> all = new HashSet<>();
    platformGrant(jwt).ifPresent(all::add);
    tableGrant(jwt).ifPresent(all::add);
    return Optional.of(new Actor(jwt.getSubject(), all, emailVerified(jwt), profile(jwt)));
  }

  /**
   * O rol da táboa no concello da petición, lido unha vez por petición. Sen concello (rutas da
   * plataforma, datos do despregue) non se le nada.
   */
  private Optional<RoleGrant> tableGrant(Jwt jwt) {
    Optional<TenantRef> tenant = TenantContext.current();
    if (tenant.isEmpty() || tenant.get().slug() == null || RESOLVING.get()) {
      return Optional.empty();
    }
    TenantRef municipality = tenant.get();
    String slug = Objects.requireNonNull(municipality.slug());
    String subject = jwt.getSubject();
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    String key = REQUEST_ATTRIBUTE + municipality.id();
    if (request != null
        && request.getAttribute(key, RequestAttributes.SCOPE_REQUEST) instanceof Lookup cached
        && cached.subject().equals(subject)) {
      return cached.grant();
    }
    Optional<Role> role;
    RESOLVING.set(true);
    try {
      role = staffRoles.get().activeRole(subject, municipality, authTime(jwt));
    } finally {
      RESOLVING.remove();
    }
    Optional<RoleGrant> grant =
        role.filter(r -> !RolePermissions.isGlobal(r)).map(r -> new RoleGrant(slug, r));
    if (request != null) {
      request.setAttribute(key, new Lookup(subject, grant), RequestAttributes.SCOPE_REQUEST);
    }
    return grant;
  }

  /** O rol da táboa xa lido nesta petición. */
  private record Lookup(String subject, Optional<RoleGrant> grant) {}

  /**
   * A administración da plataforma, lida unha vez por petición (tamén sen concello: as rutas da
   * plataforma non o teñen).
   */
  private Optional<RoleGrant> platformGrant(Jwt jwt) {
    String subject = jwt.getSubject();
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    // Antes que RESOLVING: o staff.sign-in que se rexistra mentres se le o rol de persoal leva
    // tamén a administración da plataforma xa lida nesta petición.
    if (request != null
        && request.getAttribute(PLATFORM_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            instanceof Lookup cached
        && cached.subject().equals(subject)) {
      return cached.grant();
    }
    if (RESOLVING.get()) {
      return Optional.empty();
    }
    boolean admin;
    RESOLVING.set(true);
    try {
      admin =
          platformAdmins
              .get()
              .isPlatformAdmin(subject, emailVerified(jwt) ? text(jwt, "email") : null);
    } finally {
      RESOLVING.remove();
    }
    Optional<RoleGrant> grant =
        admin ? Optional.of(new RoleGrant(RoleGrant.ANY, Role.PLATFORM_ADMIN)) : Optional.empty();
    if (request != null) {
      request.setAttribute(
          PLATFORM_ATTRIBUTE, new Lookup(subject, grant), RequestAttributes.SCOPE_REQUEST);
    }
    return grant;
  }

  /**
   * Nome, correo e idioma (OIDC Core 5.1): {@code name} ou, sen el, {@code given_name} e {@code
   * family_name}. Só cadeas non baleiras; o resto, ausente.
   */
  static Actor.Profile profile(Jwt jwt) {
    String name = text(jwt, "name");
    if (name == null) {
      String joined =
          Stream.of(text(jwt, "given_name"), text(jwt, "family_name"))
              .filter(Objects::nonNull)
              .collect(Collectors.joining(" "));
      name = joined.isEmpty() ? null : joined;
    }
    return new Actor.Profile(name, text(jwt, "email"), text(jwt, "locale"));
  }

  private static @Nullable String text(Jwt jwt, String claim) {
    return jwt.getClaims().get(claim) instanceof String value && !value.isBlank()
        ? value.strip()
        : null;
  }

  /**
   * {@code email_verified} (OIDC Core 5.1) é un booleano JSON. Calquera outra forma (ausente, cadea
   * {@code "true"}) non conta: no dubidoso, a persoa non pode reservar (ADR-0025, punto 3).
   */
  static boolean emailVerified(Jwt jwt) {
    return Boolean.TRUE.equals(jwt.getClaims().get("email_verified"));
  }

  /**
   * {@code auth_time} (OIDC Core 2): segundos desde a época, o momento da última autenticación.
   * Calquera outra forma, nulo (non se rexistra o acceso).
   */
  static @Nullable Instant authTime(Jwt jwt) {
    Object value = jwt.getClaims().get("auth_time");
    if (value instanceof Number seconds) {
      return Instant.ofEpochSecond(seconds.longValue());
    }
    return value instanceof Instant instant ? instant : null;
  }
}
