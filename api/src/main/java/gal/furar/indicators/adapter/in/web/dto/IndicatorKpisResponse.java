// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * As cinco cifras.
 *
 * @param bookings reservas confirmadas que empezan no período
 * @param occupancy ocupación media
 * @param reservedHours horas reservadas, en minutos ponderados polo uso
 * @param people persoas distintas do período enteiro
 * @param paidCents cobrado, en céntimos
 */
public record IndicatorKpisResponse(
    IndicatorCountKpiResponse bookings,
    IndicatorOccupancyKpiResponse occupancy,
    IndicatorMinutesKpiResponse reservedHours,
    IndicatorCountKpiResponse people,
    IndicatorCountKpiResponse paidCents) {}
