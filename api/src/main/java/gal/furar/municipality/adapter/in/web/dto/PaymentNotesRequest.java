// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * «Como se paga» e «Como se devolve» (con {@code If-Match}); sen nota, o texto por defecto de
 * Furar. {@code scope}: igual para todas as instalacións ou distinto en cada unha.
 */
public record PaymentNotesRequest(
    @Nullable @Valid NoteRequest payment,
    @Nullable @Valid NoteRequest refund,
    @Nullable
        @Pattern(regexp = "^(ALL|FACILITY)?$")
        @Schema(
            description =
                "ALL: os textos do concello valen para todas as instalacións; FACILITY: cada"
                    + " instalación leva os seus. Sen valor, o que xa había",
            allowableValues = {"ALL", "FACILITY"})
        String scope) {}
