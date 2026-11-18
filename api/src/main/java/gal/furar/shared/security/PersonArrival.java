// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

/**
 * SPI: unha persoa chega a un concello coa súa conta e o correo verificado (ADR-0031, punto 7).
 * Impleméntaa {@code booking}, que lle pasa á conta as reservas feitas no mostrador a nome dese
 * correo; sen ela, {@link NoPersonArrival}. Chámase antes do controlador, só nas rutas da persoa no
 * concello ({@code …/municipalities/{slug}/me/…}), co concello xa fixado e fóra de toda
 * transacción: a implementación abre a súa propia transacción curta. Repetila non cambia nada.
 */
public interface PersonArrival {

  /**
   * @param subject {@code sub} do token
   * @param verifiedEmail o correo do token, xa verificado
   */
  void arrived(String subject, String verifiedEmail);
}
