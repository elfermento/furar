// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Sección Datos (con {@code If-Match}), enteira: deportes, tipo, RIDEGA, teléfono, correo, reserva
 * por internet ou ocos libres (nunca as dúas), cuberta (nulo = sen dato) e campos propios ({@code
 * customFields} substitúe os valores; sen el, quedan sen valor). Que non vale dío o dominio, con
 * {@code errors[]} por campo ({@code customFields.<código>} nos propios).
 */
public record UpdateFacilityDetailsRequest(
    @NotNull @Size(max = 30) List<@Valid @NotNull SportRefRequest> sports,
    @Nullable FacilityTypeValue type,
    @Nullable @Size(max = 30) String ridegaCode,
    @Nullable @Size(max = 30) String phone,
    @Nullable @Size(max = 254) String email,
    @NotNull Boolean onlineBooking,
    @NotNull Boolean showFreeSlots,
    @Nullable Boolean covered,
    @Nullable @Schema(description = "Valores dos campos propios, por código")
        Map<String, Object> customFields) {}
