// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros dos indicadores (plan do dominio 3, §2.3). Cada código ten texto en {@code
 * messages/indicators_*.properties} e en {@code gl.json}/{@code es.json}.
 */
public enum IndicatorsError implements ErrorCode {
  /**
   * Rango libre sen {@code from} ou {@code to}, con {@code to < from} ou de máis de 366 días, ou
   * datas fóra de {@code 2000-01-01 … hoxe + 2 anos}.
   */
  PERIOD_INVALID("indicators.period-invalid", 400),
  /**
   * Instalación ou uso que non son do concello (ou que non existen: a mesma resposta, sen o id),
   * uso doutra instalación ca a escollida, usos repetidos ou máis de 50.
   */
  FILTER_INVALID("indicators.filter-invalid", 400);

  private final String code;
  private final int status;

  IndicatorsError(String code, int status) {
    this.code = code;
    this.status = status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return status;
  }
}
