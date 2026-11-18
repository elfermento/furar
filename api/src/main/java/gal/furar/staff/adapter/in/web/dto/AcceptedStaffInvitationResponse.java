// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * A invitación aceptada.
 *
 * @param role o rol no concello, que vale desde a seguinte petición
 */
public record AcceptedStaffInvitationResponse(StaffRoleValue role) {}
