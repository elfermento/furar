// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * As cifras dun concello no período, as mesmas do seu panel.
 *
 * @param slug o identificador do concello no URL
 * @param name o nome do concello
 * @param bookings reservas confirmadas que empezan no período
 * @param occupancy ocupación media
 * @param reservedMinutes horas reservadas, en minutos ponderados polo uso
 * @param people persoas distintas que reservaron no concello
 * @param paidCents cobrado, en céntimos
 */
public record PlatformMunicipalityIndicatorsResponse(
    String slug,
    String name,
    long bookings,
    IndicatorOccupancyResponse occupancy,
    double reservedMinutes,
    long people,
    long paidCents) {}
