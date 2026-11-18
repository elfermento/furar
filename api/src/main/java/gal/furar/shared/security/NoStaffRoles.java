// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import gal.furar.shared.tenancy.TenantRef;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link StaffRoles} sen pertenzas: o que usa {@code JwtCurrentActor} se non hai ningunha
 * implementación (probas dun módulo illado, {@code @ApplicationModuleTest(STANDALONE)}). Ninguén
 * ten rol de persoal pola táboa.
 */
public final class NoStaffRoles implements StaffRoles {

  @Override
  public Optional<Role> activeRole(
      String subject, TenantRef municipality, @Nullable Instant authTime) {
    return Optional.empty();
  }
}
