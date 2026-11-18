// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

/**
 * Resposta a Keycloak (ADR-0043).
 *
 * @param allowed se pode eliminar a conta
 */
public record AccountDeletionAllowedResponse(boolean allowed) {}
