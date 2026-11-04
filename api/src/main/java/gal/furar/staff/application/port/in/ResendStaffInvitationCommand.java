// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.domain.StaffMemberId;

/** Reenviar a invitación: {@code expectedVersion} sae do {@code If-Match}. */
public record ResendStaffInvitationCommand(StaffMemberId id, long expectedVersion) {}
