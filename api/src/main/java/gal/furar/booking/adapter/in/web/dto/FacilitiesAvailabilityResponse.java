// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Os ocos libres dunha páxina de instalacións nun día (portada e listado, §1.2.18): só as que se
 * reservan por internet, en orde alfabética. As pechadas temporalmente saen con 0.
 */
public record FacilitiesAvailabilityResponse(
    LocalDate date,
    LocalDate today,
    Instant now,
    int page,
    int size,
    List<FacilityFreeSlotsResponse> items) {}
