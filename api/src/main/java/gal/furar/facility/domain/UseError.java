// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros dos usos da instalación (D1-12). Cada código ten texto en
 * messages/facility_uses_*.properties e en gl.json/es.json.
 */
public enum UseError implements ErrorCode {
  NOT_FOUND("facility.use-not-found", 404),
  /** Datos do uso fóra das regras, campo a campo en {@code errors[]}. */
  INVALID("facility.use-invalid", 422),
  NAME_TAKEN("facility.use-name-taken", 409),
  PART_NAME_TAKEN("facility.use-part-name-taken", 409),
  PARTS_REQUIRED("facility.use-parts-required", 422),
  CAPACITY_REQUIRED("facility.use-capacity-required", 422),
  DATES_REQUIRED("facility.use-dates-required", 422),
  BLOCKS_OTHERS_NEEDS_DATES("facility.use-blocks-others-needs-dates", 422),
  PERIOD_OVERLAP("facility.use-period-overlap", 422),
  /** Outro uso que bloquea os demais ten datas que se pisan con estas (P2). */
  EXCLUSIVE_USE_OVERLAP("facility.exclusive-use-overlap", 409),
  /** O cambio deixaría mal reservas futuras: crea un uso novo (P1). */
  HAS_BOOKINGS("facility.use-has-bookings", 409),
  /** Un uso arquivado xa non se edita. */
  ARCHIVED("facility.use-archived", 409),
  /** Prezo de pago sen importe ou fóra de rango, ou importe sen ser de pago (D2-02). */
  PRICE_INVALID("facility.use-price-invalid", 422),
  /** Unha norma fóra de rango, ou o límite por persoa sen número ou sen período (D2-02). */
  RULE_INVALID("facility.use-rule-invalid", 422),
  /** Sen ningún día da semana (D2-02). */
  WEEKDAYS_INVALID("facility.use-weekdays-invalid", 422);

  private final String code;
  private final int status;

  UseError(String code, int status) {
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
