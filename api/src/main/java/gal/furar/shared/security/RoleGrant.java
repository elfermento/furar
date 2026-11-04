// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.util.Objects;
import java.util.Optional;

/**
 * Un rol nun concello ({@code naron:MANAGER}) ou en todos ({@code *:PLATFORM_ADMIN}). O ámbito
 * {@link #ANY} só vale para os roles globais e un rol global só vale con {@link #ANY}: {@code
 * *:MANAGER} non dá xestión de todos os concellos, rexéitase (ADR-0007).
 *
 * @param scope slug do concello ou {@link #ANY}
 * @param role rol
 */
public record RoleGrant(String scope, Role role) {

  public static final String ANY = "*";

  public RoleGrant {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(role, "role");
    if (!consistent(scope, role)) {
      throw new IllegalArgumentException("Role " + role + " cannot have scope " + scope);
    }
  }

  /** Le {@code slug:ROL}; devolve baleiro se o texto non é válido. */
  public static Optional<RoleGrant> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    int colon = text.lastIndexOf(':');
    if (colon <= 0 || colon == text.length() - 1) {
      return Optional.empty();
    }
    String scope = text.substring(0, colon);
    Role role;
    try {
      role = Role.valueOf(text.substring(colon + 1));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    if (!consistent(scope, role)) {
      return Optional.empty();
    }
    return Optional.of(new RoleGrant(scope, role));
  }

  /** Global ⇔ {@code *}: nin {@code naron:PLATFORM_ADMIN} nin {@code *:MANAGER}. */
  private static boolean consistent(String scope, Role role) {
    return RolePermissions.isGlobal(role) == ANY.equals(scope);
  }

  /** Se este rol vale no concello indicado ({@code null} = fóra de concello). */
  public boolean appliesTo(String municipalitySlug) {
    return ANY.equals(scope) || scope.equals(municipalitySlug);
  }
}
