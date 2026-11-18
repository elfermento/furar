// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.account;

import java.util.Map;
import java.util.UUID;

/**
 * SPI: o que un módulo borra ou anonimiza cando se elimina unha conta (ADR-0043), despois de {@code
 * close_account}, que xa limpou o común. Hoxe a implementa {@code booking} (as reservas sen pago).
 * Chámaa {@code people} unha vez por conta eliminada, fóra de toda transacción e sen concello: a
 * implementación traballa en cada concello coa súa RLS e transaccións curtas. Ten que ser
 * idempotente: Keycloak pode avisar máis dunha vez da mesma conta.
 */
public interface AccountClosureStep {

  /** Clave dos detalles do rexistro de actividade onde vai a cifra deste paso. */
  String detail();

  /**
   * @param subject o {@code sub} da conta xa eliminada en Keycloak
   * @return cantas filas tocou en cada concello (os concellos sen ningunha non saen)
   */
  Map<UUID, Long> close(String subject);
}
