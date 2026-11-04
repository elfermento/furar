// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.shared.domain.Language;
import gal.furar.staff.domain.StaffRole;
import org.jspecify.annotations.Nullable;

/**
 * Invitar a unha persoa.
 *
 * @param email o correo tal como se escribiu (o caso de uso normalízao)
 * @param role o rol no concello
 * @param language o idioma do correo
 */
public record InviteStaffMemberCommand(@Nullable String email, StaffRole role, Language language) {}
