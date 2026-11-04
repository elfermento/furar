// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * A administración da plataforma na base de Furar (ADR-0028): táboa da plataforma, sen concello.
 * Sempre dentro dunha transacción de quen chama.
 */
public interface PlatformAdminRepository {

  /** Se a conta é administración da plataforma. */
  boolean isAdmin(String subject);

  /**
   * Primeira administración (ADR-0028, punto 2): dálle o rol á conta só se aínda non hai ningunha e
   * nunca se deu por esta vía. Atómico: dúas peticións ao mesmo tempo non dan dúas.
   *
   * @return se esta chamada deu o rol
   */
  boolean grantFirst(UUID id, String subject, Instant at);
}
