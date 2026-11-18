// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A entidade titular dunha reserva (ADR-0039).
 *
 * @param seriesId nunha sesión, o id da súa tempada (vai sempre: unha sesión non se move, só se
 *     cancela); nulo nunha reserva extra
 * @param series nunha sesión, a súa tempada (no detalle da xestión, só con {@code
 *     booking.organizations}); nula nunha reserva extra
 */
public record BookingOrganizationResponse(
    UUID id, String name, @Nullable UUID seriesId, @Nullable BookingSeasonResponse series) {}
