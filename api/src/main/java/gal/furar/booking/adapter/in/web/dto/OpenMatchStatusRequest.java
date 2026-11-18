// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** «Deixar de buscar» ({@code CLOSED}) ou volver buscar xente ({@code OPEN}). */
public record OpenMatchStatusRequest(
    @NotNull @Schema(allowableValues = {"OPEN", "CLOSED"}) OpenMatchStatusValue status) {}
