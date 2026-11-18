// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * Unha ocupación en bruto: a porcentaxe é {@code reservedMinutes / openMinutes} (sen valor se
 * {@code openMinutes} é 0).
 *
 * @param reservedMinutes minutos reservados co peso do uso (unha parte de dúas, a metade; unha
 *     praza de 30, 1/30), sen pasar dos abertos en cada instalación
 * @param openMinutes minutos abertos (tempada, festivos e excepcións, menos bloqueos)
 */
public record IndicatorOccupancyResponse(double reservedMinutes, long openMinutes) {}
