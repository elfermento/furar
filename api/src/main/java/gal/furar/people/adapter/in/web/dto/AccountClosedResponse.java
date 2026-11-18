// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

/**
 * Resposta a Keycloak (ADR-0043): a limpeza fíxose (ou xa estaba feita).
 *
 * @param closed sempre verdadeiro; un erro responde 500 e Keycloak volve avisar
 */
public record AccountClosedResponse(boolean closed) {}
