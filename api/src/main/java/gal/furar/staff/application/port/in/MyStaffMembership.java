// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.domain.StaffRole;

/**
 * Un concello onde a persoa da sesión é persoal activo (cambiar de concello desde o menú da persoa;
 * ADR-0027, revisión do 2026-11-03).
 *
 * @param slug identificador do concello no URL
 * @param name nome do concello
 * @param role o seu rol nese concello
 */
public record MyStaffMembership(String slug, String name, StaffRole role) {}
