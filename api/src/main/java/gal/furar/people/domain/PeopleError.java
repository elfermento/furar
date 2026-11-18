// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros do módulo (ADR-0031, punto 10). Cada código ten texto en {@code
 * messages/people_*.properties} e en {@code gl.json}/{@code es.json}.
 */
public enum PeopleError implements ErrorCode {
  /** A ficha non é desta conta, está desactivada ou non existe. */
  DEPENDENT_NOT_FOUND("people.dependent-not-found", 404),
  DEPENDENT_LIMIT("people.dependent-limit", 422),
  /** Xa hai outra ficha activa co mesmo nome e ano. */
  DEPENDENT_DUPLICATE("people.dependent-duplicate", 409),
  /** Falta a declaración «Esta persoa está ao meu cargo». */
  DEPENDENT_DECLARATION_REQUIRED("people.dependent-declaration-required", 422),
  DEPENDENT_BIRTH_YEAR_INVALID("people.dependent-birth-year-invalid", 422);

  private final String code;
  private final int status;

  PeopleError(String code, int status) {
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
