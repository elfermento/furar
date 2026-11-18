// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * O diálogo «Buscar xente para xogar» (ADR-0040): «Xa somos» (con quen organiza), mínimo e máximo
 * de persoas totais (de 2 a 40), nivel (sen el, «Sen indicar»), título, descrición en texto plano e
 * «Con aprobación». Os números imposibles dios o dominio: 422 {@code booking.match-counts-invalid}.
 */
public record OpenMatchRequest(
    @NotNull Integer knownCount,
    @NotNull Integer minimum,
    @NotNull Integer maximum,
    @Nullable OpenMatchLevelValue level,
    @Nullable @Size(max = 80) String title,
    @Nullable @Size(max = 1000) String description,
    @NotNull Boolean approval) {}
