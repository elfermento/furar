// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.in;

import java.util.List;

/**
 * Se se pode eliminar a conta (ADR-0043): a web pregúntao antes de ir a Keycloak e Keycloak, outra
 * vez, antes de borrala.
 */
public interface AccountDeletionUseCase {

  /** O que impide eliminar a conta da sesión, por tipo e concello; baleira se nada. */
  List<AccountDeletionView> mine();

  /**
   * Se Keycloak pode eliminar esta conta (chámao polo porto interno, coa clave).
   *
   * @param subject o {@code sub} da conta
   */
  boolean allowed(String subject);
}
