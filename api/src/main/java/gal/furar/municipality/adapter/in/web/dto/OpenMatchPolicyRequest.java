// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * «Buscar xente para xogar» enteiro (con {@code If-Match}). O rango das horas (de 1 a 72) dio o
 * dominio: 422 {@code municipality.open-match-policy-invalid}.
 */
public record OpenMatchPolicyRequest(@NotNull Boolean enabled, @NotNull Integer cancelHours) {}
