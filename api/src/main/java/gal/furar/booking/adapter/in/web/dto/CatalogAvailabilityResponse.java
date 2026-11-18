// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Os ocos libres dun día nas instalacións dunha páxina de concellos da portada común (ADR-0030): só
 * as que se reservan por internet; as pechadas temporalmente saen con 0. Hai máis páxinas mentres
 * {@code hasNext} sexa verdadeiro.
 */
public record CatalogAvailabilityResponse(
    LocalDate date,
    LocalDate today,
    Instant now,
    int page,
    int size,
    boolean hasNext,
    List<FacilityFreeSlotsResponse> items) {}
