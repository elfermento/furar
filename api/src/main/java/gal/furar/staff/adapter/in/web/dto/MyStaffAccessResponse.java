// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * O acceso da persoa da sesión á xestión do concello.
 *
 * @param role o seu rol no concello; nulo se non é persoal del
 * @param previousSignInAt o acceso anterior ao desta sesión («O teu último acceso foi o …»); nulo
 *     no primeiro
 * @param workAccount se a conta é persoal activo nalgún concello (este ou outro): a reserva da
 *     veciñanza avisa antes de que o intente, porque a API respondería {@code
 *     booking.staff-account}
 */
public record MyStaffAccessResponse(
    @Nullable StaffRoleValue role, @Nullable Instant previousSignInAt, boolean workAccount) {}
