// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * As catro cifras sumadas de todos os concellos, coa mesma forma ca no panel do concello. Sen
 * persoas distintas: unha persoa que reserva en dous concellos contaría dúas veces.
 *
 * @param bookings reservas confirmadas que empezan no período
 * @param occupancy ocupación media: minutos reservados e abertos sumados
 * @param reservedHours horas reservadas, en minutos ponderados polo uso
 * @param paidCents cobrado, en céntimos
 */
public record PlatformIndicatorKpisResponse(
    IndicatorCountKpiResponse bookings,
    IndicatorOccupancyKpiResponse occupancy,
    IndicatorMinutesKpiResponse reservedHours,
    IndicatorCountKpiResponse paidCents) {}
