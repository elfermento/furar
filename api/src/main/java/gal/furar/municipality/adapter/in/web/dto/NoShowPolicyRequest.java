// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Penalización das faltas enteira (con {@code If-Match}). Os rangos (de 1 a 20 faltas e de 1 a 365
 * días) dios o dominio: 422 {@code municipality.no-show-policy-invalid}.
 */
public record NoShowPolicyRequest(
    @NotNull Boolean enabled,
    @NotNull Integer threshold,
    @NotNull Integer windowDays,
    @NotNull Integer blockDays) {}
