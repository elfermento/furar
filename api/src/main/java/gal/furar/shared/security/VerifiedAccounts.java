// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * SPI: as contas coñecidas polo seu correo verificado (ADR-0031, punto 4). Impleméntaa {@code
 * people}; sen ela (probas dun módulo illado), {@link NoVerifiedAccounts}. Chámase unha vez por
 * petición, antes do controlador e fóra de toda transacción, só co correo verificado: a
 * implementación abre a súa propia transacción curta e escribe unha vez por sesión. É unha axuda
 * para o mostrador, non autoriza nada: se non pode escribir, a petición segue.
 */
public interface VerifiedAccounts {

  /**
   * A conta entrou con este correo verificado.
   *
   * @param subject {@code sub} do token
   * @param verifiedEmail o correo do token, xa verificado
   * @param authTime {@code auth_time} do token; nulo = o token non o trae (non se escribe)
   */
  void seen(String subject, String verifiedEmail, @Nullable Instant authTime);
}
