// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * O período do panel: a semana ISO, o mes, o trimestre ou o ano que conteñen {@code date}, ou un
 * rango libre de 1 a 366 días ({@code CUSTOM}, con {@code from} e {@code to}).
 */
public enum IndicatorPeriodKindValue {
  WEEK,
  MONTH,
  QUARTER,
  YEAR,
  CUSTOM
}
