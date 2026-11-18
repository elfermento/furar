// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Os ocos libres dunha instalación nun día.
 *
 * @param freeCount as horas nas que algún dos usos que conta o listado está libre: os que ofrece a
 *     ficha ou, con {@code sport}, os que teñen algún dos deportes pedidos
 * @param firstStarts as tres primeiras desas horas
 */
public record FacilityFreeSlotsResponse(
    UUID facilityId, int freeCount, List<SlotStartResponse> firstStarts) {}
