// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import org.jspecify.annotations.Nullable;

/**
 * O período que se escolle no panel (plan do dominio 3, §1.2.5): a unidade natural que contén unha
 * data ou un rango libre de datas («Datas…»).
 */
public enum PeriodKind {
  WEEK(PeriodUnit.WEEK),
  MONTH(PeriodUnit.MONTH),
  QUARTER(PeriodUnit.QUARTER),
  YEAR(PeriodUnit.YEAR),
  CUSTOM(null);

  private final @Nullable PeriodUnit unit;

  PeriodKind(@Nullable PeriodUnit unit) {
    this.unit = unit;
  }

  /** A unidade natural do período; {@code null} no rango libre. */
  public @Nullable PeriodUnit unit() {
    return unit;
  }
}
