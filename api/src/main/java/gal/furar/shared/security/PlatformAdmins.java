// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import org.jspecify.annotations.Nullable;

/**
 * SPI: se a persoa é administración da plataforma ({@link Role#PLATFORM_ADMIN}), lido da base de
 * Furar en cada petición (ADR-0028), como {@link StaffRoles} le o persoal. Keycloak só autentica:
 * ningún claim do token dá este rol. A implementa {@code staff}; sen ela (probas dun módulo
 * illado), {@link NoPlatformAdmins}. {@code JwtCurrentActor} chámaa unha vez por petición, antes da
 * transacción do caso de uso, así que a implementación abre a súa propia transacción curta.
 */
public interface PlatformAdmins {

  /**
   * Se a persoa é administración da plataforma. Primeiro acceso (ADR-0028, punto 2): se aínda non
   * hai ningunha administración e o correo verificado da conta é o de {@code FURAR_ADMIN_EMAIL}, a
   * implementación dálle o rol (unha soa vez, no rexistro de actividade) e devolve verdadeiro.
   *
   * @param subject {@code sub} do token
   * @param verifiedEmail correo do token só se {@code email_verified} é verdadeiro; nulo se non
   */
  boolean isPlatformAdmin(String subject, @Nullable String verifiedEmail);
}
