// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * Resposta a Keycloak (ADR-0029).
 *
 * @param required se a conta ten que entrar co segundo factor
 */
public record SecondFactorResponse(boolean required) {}
