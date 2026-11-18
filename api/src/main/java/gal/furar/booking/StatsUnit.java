// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

/**
 * Unidade dos agregados por período de {@link BookingStatistics}, en Europe/Madrid. A semana vai de
 * luns a domingo (ISO); o trimestre empeza en xaneiro, abril, xullo ou outubro.
 */
public enum StatsUnit {
  DAY,
  WEEK,
  MONTH,
  QUARTER,
  YEAR
}
