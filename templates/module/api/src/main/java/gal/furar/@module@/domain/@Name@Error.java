// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.domain;

import gal.furar.shared.domain.ErrorCode;

/** Erros do módulo. Cada código ten texto en messages_*.properties e en gl.json/es.json. */
public enum @Name@Error implements ErrorCode {
  NOT_FOUND("@module@.not-found", 404),
  NAME_INVALID("@module@.name-invalid", 422);

  private final String code;
  private final int status;

  @Name@Error(String code, int status) {
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
