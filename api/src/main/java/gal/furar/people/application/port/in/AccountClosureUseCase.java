// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.in;

/**
 * Keycloak eliminou unha conta (ADR-0043): bórranse e desligan os seus datos, menos o que hai que
 * conservar. Idempotente: Keycloak pode avisar máis dunha vez.
 */
public interface AccountClosureUseCase {

  /**
   * @param subject o {@code sub} da conta xa eliminada
   * @return cantas filas tocou en total (0 se xa estaba feito)
   */
  long closed(String subject);
}
