// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * O acceso da persoa da sesión á administración da plataforma.
 *
 * @param platformAdmin se é administración da plataforma
 */
public record MyPlatformAccessResponse(boolean platformAdmin) {}
