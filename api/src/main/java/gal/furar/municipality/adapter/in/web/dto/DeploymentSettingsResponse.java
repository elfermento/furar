// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Datos públicos do despregue para a web (sen concello). */
public record DeploymentSettingsResponse(
    MapSettingsResponse map,
    @Schema(
            description =
                "Correo de soporte que a web mostra no pé (FURAR_SUPPORT_EMAIL); null se non hai",
            example = "soporte@example.org")
        @Nullable String supportEmail,
    @Schema(
            description =
                "Se existe «¿Es un concello?»: a franxa da portada común, a ligazón do pé e a"
                    + " páxina da solicitude (FURAR_JOIN_REQUEST_EMAIL; o correo nunca sae)")
        boolean joinRequests) {}
