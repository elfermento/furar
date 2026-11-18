// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

/** Códigos dos erros por campo da validación de entrada ({@code errors[].code}). */
public enum ValidationError implements ErrorCode {
  REQUIRED("validation.required"),
  SIZE("validation.size"),
  PATTERN("validation.pattern"),
  RANGE("validation.range"),
  INVALID("validation.invalid");

  private final String code;

  ValidationError(String code) {
    this.code = code;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return 400;
  }
}
