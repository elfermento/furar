// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Datos legais do concello (ADR-0017 punto 8); {@code null} = sen valor, usa os do despregue. Os
 * textos propios das páxinas van á parte ({@code LegalTextsResponse}): só os pide a páxina que os
 * pinta.
 */
@Schema(description = "Datos legais do concello e modo de cada páxina legal")
@SuppressWarnings("java:S107")
public record LegalDetailsResponse(
    @Nullable String ineCode,
    @Nullable String dir3Code,
    @Nullable String legalEntityName,
    @Nullable String eOfficeUrl,
    @Nullable String nif,
    @Nullable String address,
    @Nullable String generalEmail,
    @Nullable String dpoEmail,
    @Nullable String dpoPhone,
    @Nullable String processor,
    @Nullable String accessibilityEmail,
    @Nullable String accessibilityComplaintUrl,
    @Nullable String accessibilityUnit,
    @Schema(allowableValues = {"FURAR", "TEXT", "URL", "NONE"}) String privacyMode,
    @Nullable String privacyUrl,
    @Schema(allowableValues = {"FURAR", "TEXT", "URL", "NONE"}) String accessibilityMode,
    @Nullable String accessibilityUrl,
    @Schema(allowableValues = {"FURAR", "TEXT", "URL", "NONE"}) String legalNoticeMode,
    @Nullable String legalNoticeUrl) {}
