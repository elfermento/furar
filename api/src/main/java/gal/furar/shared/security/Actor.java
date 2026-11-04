// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Persoa autenticada que fai a petición.
 *
 * @param subject identificador estable (claim {@code sub})
 * @param grants roles por concello que valen nesta petición
 * @param emailVerified se o token di que o correo está verificado ({@code email_verified}); os
 *     casos de uso da persoa ({@link PersonUseCase}) pídeno (ADR-0025, punto 3)
 * @param profile nome, correo e idioma do token: os que copia unha reserva (ADR-0025, punto 3)
 */
public record Actor(String subject, Set<RoleGrant> grants, boolean emailVerified, Profile profile) {

  public Actor {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(profile, "profile");
    grants = Set.copyOf(grants);
  }

  /** Sen datos de contacto: o persoal e as probas que non reservan. */
  public Actor(String subject, Set<RoleGrant> grants, boolean verified) {
    this(subject, grants, verified, Profile.NONE);
  }

  /** Sen correo verificado: o persoal e as probas que non reservan. */
  public Actor(String subject, Set<RoleGrant> grants) {
    this(subject, grants, false);
  }

  /** Se ten o permiso no concello indicado ({@code null} = acción fóra de concello). */
  public boolean can(PermissionKey permission, @Nullable String municipalitySlug) {
    return grants.stream()
        .anyMatch(
            g ->
                (RoleGrant.ANY.equals(g.scope())
                        || (municipalitySlug != null && g.appliesTo(municipalitySlug)))
                    && RolePermissions.allows(g.role(), permission));
  }

  /**
   * Se ten algún rol propio dese concello (veciño, persoal, xestión). Os roles globais ({@code *})
   * non contan: non vinculan a persoa a ningún concello concreto.
   */
  public boolean hasRoleIn(@Nullable String municipalitySlug) {
    return municipalitySlug != null
        && grants.stream().anyMatch(g -> g.scope().equals(municipalitySlug));
  }

  /**
   * Os datos da persoa que trae o token (OIDC Core 5.1), tal como veñen: calquera pode faltar.
   *
   * @param name nome para mostrar ({@code name}, ou {@code given_name} e {@code family_name})
   * @param email correo ({@code email}); verificado só se {@link Actor#emailVerified()}
   * @param locale idioma preferido ({@code locale}), por exemplo {@code gl} ou {@code es}
   */
  public record Profile(@Nullable String name, @Nullable String email, @Nullable String locale) {

    /** Sen ningún dato. */
    public static final Profile NONE = new Profile(null, null, null);
  }
}
