// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos legais do concello (ADR-0017 punto 8), todos opcionais. Baleiro = sen valor: a web usa os
 * do despregue. As URL, só {@code https}. Cada páxina legal leva o seu modo ({@code FURAR} se non
 * vén) e o enderezo da páxina propia, obrigatorio só no modo {@code URL}.
 */
@Schema(description = "Datos legais do concello e modo de cada páxina legal")
@SuppressWarnings("java:S107")
public record LegalDetailsRequest(
    @Pattern(regexp = "^([A-Z][A-Z0-9]{8})?$")
        @Schema(description = "Código DIR3", example = "L01150548")
        String dir3Code,
    @Size(max = 200)
        @Schema(
            description =
                "Entidade responsable: o concello ou o patronato, organismo ou empresa que xestiona"
                    + " o servizo")
        String legalEntityName,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Sede electrónica")
        String eOfficeUrl,
    @Size(max = 20)
        @Schema(
            description =
                "NIF da entidade responsable: letra de entidade, 7 díxitos e control. Admite"
                    + " minúsculas, espazos, puntos e guións; gárdase normalizado",
            example = "P1505400J")
        String nif,
    @Size(max = 200) @Schema(description = "Domicilio (aviso legal)") String address,
    @Size(max = 200) @Email @Schema(description = "Correo xeral da entidade (aviso legal)")
        String generalEmail,
    @Size(max = 200) @Email @Schema(description = "Correo do DPD da entidade") String dpoEmail,
    @Pattern(regexp = "^(\\+?[0-9 ]{9,15})?$") @Schema(description = "Teléfono do DPD da entidade")
        String dpoPhone,
    @Size(max = 200)
        @Schema(
            description =
                "Encargado que aloxa e opera o servizo (RGPD art. 28); baleiro se o aloxa a propia"
                    + " entidade responsable")
        String processor,
    @Size(max = 200) @Email @Schema(description = "Correo de accesibilidade")
        String accessibilityEmail,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Procedemento de reclamación de accesibilidade")
        String accessibilityComplaintUrl,
    @Size(max = 200)
        @Schema(description = "Unidade responsable de accesibilidade (art. 16 RD 1112/2018)")
        String accessibilityUnit,
    @Pattern(regexp = "^(\\+?[0-9 ]{9,15})?$")
        @Schema(
            description = "Teléfono xeral da entidade (aviso legal, privacidade, accesibilidade)")
        String generalPhone,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Rexistro de actividades de tratamento (LOPDGDD art. 31.2)")
        String processingRegisterUrl,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Normativa de uso das instalacións (condicións de uso)")
        String useRegulationUrl,
    @Pattern(regexp = "^(FURAR|TEXT|URL|NONE)?$")
        @Schema(
            description = "Como se publica «Privacidade»; sen valor, FURAR",
            allowableValues = {"FURAR", "TEXT", "URL", "NONE"})
        String privacyMode,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Política de privacidade propia (modo URL)")
        String privacyUrl,
    @Pattern(regexp = "^(FURAR|TEXT|URL|NONE)?$")
        @Schema(
            description = "Como se publica «Accesibilidade»; sen valor, FURAR",
            allowableValues = {"FURAR", "TEXT", "URL", "NONE"})
        String accessibilityMode,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Declaración de accesibilidade propia (modo URL)")
        String accessibilityUrl,
    @Pattern(regexp = "^(FURAR|TEXT|URL|NONE)?$")
        @Schema(
            description = "Como se publica «Aviso legal»; sen valor, FURAR",
            allowableValues = {"FURAR", "TEXT", "URL", "NONE"})
        String legalNoticeMode,
    @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+)?$")
        @Schema(description = "Aviso legal propio (modo URL)")
        String legalNoticeUrl,
    @Size(max = 200)
        @Schema(
            deprecated = true,
            description =
                "Só ficheiros furar-export anteriores ao 2026-10-29: o DPD en texto libre. Sen"
                    + " dpoEmail nin dpoPhone, tíranse del o correo e o teléfono")
        String dpoContact) {}
