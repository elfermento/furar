// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos do despregue para a páxina de privacidade (ADR-0017): só os prazos do rexistro de
 * actividade (ADR-0020, revisión do 2026-10-29), en ISO 8601 ({@code P1Y}, {@code P3Y}). O
 * responsable, o DPD, os dereitos e o encargado saen dos datos legais de cada concello (revisión do
 * 2026-11-03).
 */
public record PrivacyNoticeResponse(
    @Schema(
            description =
                "Canto se ve cada evento do rexistro de actividade na xestión"
                    + " (FURAR_AUDIT_VISIBLE); despois queda bloqueado",
            example = "P1Y")
        String activityVisible,
    @Schema(
            description =
                "Canto se garda cada evento do rexistro de actividade (FURAR_AUDIT_RETENTION);"
                    + " despois bórrase",
            example = "P3Y")
        String activityRetention) {}
