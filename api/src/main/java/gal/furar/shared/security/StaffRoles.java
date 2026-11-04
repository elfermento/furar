// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import gal.furar.shared.tenancy.TenantRef;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * SPI: o rol de persoal da persoa no concello, lido da táboa de pertenzas en cada petición
 * (ADR-0027, punto 2). A implementa {@code staff}; sen ela (probas dun módulo illado), {@link
 * NoStaffRoles}. {@code JwtCurrentActor} chámaa unha vez por petición e concello, antes da
 * transacción do caso de uso, así que a implementación abre a súa propia transacción curta (RLS).
 */
public interface StaffRoles {

  /**
   * O rol da pertenza activa ({@code MANAGER} ou {@code STAFF}) da persoa no concello.
   *
   * @param subject {@code sub} do token
   * @param municipality concello da petición
   * @param authTime {@code auth_time} do token, cando este acceso conta como entrada na xestión: se
   *     é máis novo ca o último rexistrado, a implementación garda o acceso (ADR-0027, punto 5).
   *     Nulo = non rexistrar (o token non trae o claim)
   * @return baleiro se non ten pertenza activa nese concello
   */
  Optional<Role> activeRole(String subject, TenantRef municipality, @Nullable Instant authTime);
}
