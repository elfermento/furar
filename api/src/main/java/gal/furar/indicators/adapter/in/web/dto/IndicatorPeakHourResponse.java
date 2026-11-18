// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * Unha cela do mapa de calor, sumando todas as semanas do período. Sen minutos abertos, pechada.
 *
 * @param isoWeekday de 1 (luns) a 7 (domingo)
 * @param hour de 0 a 23, en Europe/Madrid
 */
public record IndicatorPeakHourResponse(
    int isoWeekday, int hour, double reservedMinutes, long openMinutes) {}
