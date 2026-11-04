// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * O período pedido, cos dous extremos incluídos (días de Europe/Madrid).
 *
 * @param evolutionUnit a unidade da evolución das cifras (12 semanas, 12 meses, 8 trimestres ou 5
 *     anos; nun rango libre, 12 meses)
 * @param breakdownUnit a unidade da ocupación día a día e das cancelacións
 */
public record IndicatorPeriodResponse(
    IndicatorPeriodKindValue kind,
    LocalDate from,
    LocalDate to,
    IndicatorUnitValue evolutionUnit,
    IndicatorUnitValue breakdownUnit) {}
