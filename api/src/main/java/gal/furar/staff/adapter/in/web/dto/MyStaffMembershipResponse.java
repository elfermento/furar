// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * Un concello onde a persoa da sesión é persoal activo.
 *
 * @param slug identificador do concello no URL
 * @param name nome do concello
 * @param role o seu rol nese concello
 */
public record MyStaffMembershipResponse(String slug, String name, StaffRoleValue role) {}
