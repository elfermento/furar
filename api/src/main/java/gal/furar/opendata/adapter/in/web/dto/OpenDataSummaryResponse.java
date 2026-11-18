// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Resumo da páxina «Datos abertos» (ADR-0034, punto 10). {@code catalog}: hai ficha de catálogo.
 * {@code municipalities}: cantos concellos entran (1 nun concello). {@code legalEntityName}: a
 * entidade que se cita (só nun concello, se a ten). {@code customFields}: os campos públicos das
 * instalacións (só nun concello).
 */
public record OpenDataSummaryResponse(
    List<OpenDataDatasetResponse> datasets,
    OpenDataOccupancyResponse occupancy,
    boolean catalog,
    int municipalities,
    @Nullable String legalEntityName,
    List<OpenDataCustomFieldResponse> customFields) {}
