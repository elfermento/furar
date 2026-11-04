// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.domain.StaffRole;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * O acceso da persoa da sesión á xestión do concello (plan do dominio 4, §2.3).
 *
 * @param role o seu rol no concello; nulo se non é persoal del
 * @param previousSignInAt o acceso anterior ao desta sesión («O teu último acceso foi o …», ENS
 *     op.acc.6); nulo no primeiro
 * @param workAccount se a conta é persoal activo nalgún concello (este ou outro): non reserva como
 *     veciña (ADR-0027, revisión do 2026-10-30)
 */
public record MyStaffAccess(
    @Nullable StaffRole role, @Nullable Instant previousSignInAt, boolean workAccount) {}
