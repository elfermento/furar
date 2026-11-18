// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import gal.furar.shared.domain.ErrorCode;

/** Erros do filtro de concello. */
public enum TenancyError implements ErrorCode {
  MUNICIPALITY_NOT_FOUND("municipality.not-found", 404);

  private final String code;
  private final int status;

  TenancyError(String code, int status) {
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
