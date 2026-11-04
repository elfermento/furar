// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Tarxeta Datos. {@code covered} nulo = sen dato. {@code customFields}: só os campos propios que
 * pode ver quen le, por código; un campo sen valor non aparece.
 */
public record FacilityDetailsResponse(
    List<SportResponse> sports,
    @Nullable FacilityTypeValue type,
    @Nullable String ridegaCode,
    @Nullable String phone,
    @Nullable String email,
    boolean onlineBooking,
    boolean showFreeSlots,
    @Nullable Boolean covered,
    @Schema(description = "Valores dos campos propios visibles para quen le, por código")
        Map<String, Object> customFields) {}
