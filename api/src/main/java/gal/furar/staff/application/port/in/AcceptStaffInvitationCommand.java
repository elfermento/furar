// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import org.jspecify.annotations.Nullable;

/**
 * Aceptar unha invitación.
 *
 * @param token o token da ligazón, tal como chega (o caso de uso comproba a súa forma)
 */
public record AcceptStaffInvitationCommand(@Nullable String token) {

  /** Sen o token: este record nunca debe levalo a un rexistro. */
  @Override
  public String toString() {
    return "AcceptStaffInvitationCommand[token=redacted]";
  }
}
