// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** O rol novo dunha pertenza invitada ou activa. */
public record ChangeStaffRoleRequest(@NotNull StaffRoleValue role) {}
