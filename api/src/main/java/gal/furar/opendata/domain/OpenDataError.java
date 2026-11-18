// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.domain;

import gal.furar.shared.domain.ErrorCode;

/** Erros do módulo. Cada código ten texto en messages_*.properties e en gl.json/es.json. */
public enum OpenDataError implements ErrorCode {
  /** O concello ten os datos abertos apagados (ADR-0034, punto 23). */
  NOT_PUBLISHED("opendata.not-published", 404);

  private final String code;
  private final int status;

  OpenDataError(String code, int status) {
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
