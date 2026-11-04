// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffRole;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A pertenza activa dunha persoa no concello, co que precisa cada petición: o rol e os accesos.
 *
 * @param id a pertenza
 * @param role rol no concello
 * @param lastSignInAt {@code auth_time} do último acceso rexistrado
 * @param previousSignInAt o do acceso anterior: o que lle mostra a web («O teu último acceso foi o
 *     …»)
 */
public record ActiveMembership(
    StaffMemberId id,
    StaffRole role,
    @Nullable Instant lastSignInAt,
    @Nullable Instant previousSignInAt) {

  /** Se un acceso con este {@code auth_time} é novo: máis recente ca o último rexistrado. */
  public boolean isNewSignIn(Instant authTime) {
    return lastSignInAt == null || lastSignInAt.isBefore(authTime);
  }
}
