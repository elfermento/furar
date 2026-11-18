// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * As datas da ocupación: o primeiro e o último día que se poden pedir, o último mes enteiro (por
 * defecto), os meses como moito e o tamaño medio dun día.
 */
public record OpenDataOccupancyResponse(
    LocalDate availableFrom,
    LocalDate availableTo,
    LocalDate defaultFrom,
    LocalDate defaultTo,
    int maxMonths,
    OpenDataBytesResponse bytesPerDay) {}
