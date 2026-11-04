// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Exportar os eventos bloqueados dun período (LOPDGDD art. 32; ADR-0020, revisión do 2026-10-29).
 *
 * @param from comezo do período, incluído
 * @param to fin do período, excluído; como moito 366 días despois de {@code from}
 * @param reason quen o pide
 * @param reference o número do expediente, das dilixencias ou do requirimento (1 a 80 caracteres);
 *     queda no rexistro coa exportación
 */
public record ExportBlockedActivityRequest(
    @NotNull Instant from,
    @NotNull Instant to,
    @NotNull DisclosureReasonValue reason,
    @NotNull @Schema(example = "PA 123/2028", maxLength = 80) String reference) {}
