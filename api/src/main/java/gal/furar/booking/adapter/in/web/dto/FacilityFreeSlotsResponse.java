// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Os ocos libres dunha instalación nun día.
 *
 * @param freeCount as horas de inicio distintas con algún uso libre
 * @param firstStarts as tres primeiras desas horas
 */
public record FacilityFreeSlotsResponse(
    UUID facilityId, int freeCount, List<SlotStartResponse> firstStarts) {}
