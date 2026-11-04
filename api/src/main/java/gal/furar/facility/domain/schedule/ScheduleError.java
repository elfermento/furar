// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros do horario da instalación (D1-15a, D1-15b, D1-16). Cada código ten texto en
 * messages/facility_schedule_*.properties e en gl.json/es.json.
 */
public enum ScheduleError implements ErrorCode {
  SEASON_NOT_FOUND("facility.season-not-found", 404),
  /** Nome ou datas da tempada fóra das regras, campo a campo en {@code errors[]}. */
  SEASON_INVALID("facility.season-invalid", 422),
  /** Outra tempada da instalación ten días en común; {@code params} di cal. */
  SEASON_OVERLAP("facility.season-overlap", 409),
  /** Un tramo con fin antes do inicio, máis alá das 24:00 ou fóra dos cuartos de hora. */
  INTERVAL_INVALID("facility.interval-invalid", 422),
  /** Dous tramos do mesmo día que se pisan. */
  INTERVAL_OVERLAP("facility.interval-overlap", 422),
  /** O ano pedido dos festivos está fóra do rango admitido. */
  HOLIDAY_YEAR_INVALID("facility.holiday-year-invalid", 422),
  EXCEPTION_NOT_FOUND("facility.exception-not-found", 404),
  /** Data, tramo ou motivo da excepción fóra das regras, campo a campo en {@code errors[]}. */
  EXCEPTION_INVALID("facility.exception-invalid", 422),
  /** A instalación xa ten unha excepción nesa data; {@code params} di cal. */
  EXCEPTION_DATE_TAKEN("facility.exception-date-taken", 409),
  BLOCK_NOT_FOUND("facility.block-not-found", 404),
  /** Data ou motivo do bloqueo fóra das regras, campo a campo en {@code errors[]}. */
  BLOCK_INVALID("facility.block-invalid", 422),
  /**
   * O tramo do bloqueo remata antes de empezar, pasa das 24:00, non vai de cuarto en cuarto ou
   * queda baleiro co cambio de hora.
   */
  BLOCK_INVALID_PERIOD("facility.block-invalid-period", 422),
  /** Outro bloqueo da instalación pisa estas horas (P7); {@code params} di cal. */
  BLOCK_OVERLAP("facility.block-overlap", 409);

  private final String code;
  private final int status;

  ScheduleError(String code, int status) {
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
