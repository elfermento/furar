// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.ErrorCode;

/** Erros de campos personalizados: definicións (status propio) e valores ({@code errors[]}). */
public enum CustomFieldError implements ErrorCode {
  DEFINITION_NOT_FOUND("customfield.definition-not-found", 404),
  CODE_TAKEN("customfield.code-taken", 409),
  CODE_INVALID("customfield.code-invalid", 422),
  CODE_RESERVED("customfield.code-reserved", 422),
  INVALID_DEFINITION("customfield.invalid-definition", 422),
  PERSONAL_DATA_PUBLIC("customfield.personal-data-public", 422),
  /** Un campo, un sitio: máis dun uso na definición. */
  SINGLE_USAGE("customfield.single-usage", 422),
  /** O destino non admite esa visibilidade (unha reserva non ten campos públicos). */
  VISIBILITY_NOT_ALLOWED("customfield.visibility-not-allowed", 422),
  /** Cambiar de sitio un campo que xa ten valores. */
  TARGET_HAS_VALUES("customfield.target-has-values", 409),
  /** Eliminar un campo con valores gardados: desactívase. */
  HAS_VALUES("customfield.has-values", 409),
  /** Eliminar unha opción con valores gardados: desactívase. */
  OPTION_HAS_VALUES("customfield.option-has-values", 409),
  LIMIT_EXCEEDED("customfield.limit-exceeded", 422),
  // Valores (van en errors[] dun validation.failed)
  REQUIRED("customfield.required", 400),
  INVALID_TYPE("customfield.invalid-type", 400),
  TOO_SHORT("customfield.too-short", 400),
  TOO_LONG("customfield.too-long", 400),
  INVALID_FORMAT("customfield.invalid-format", 400),
  BELOW_MIN("customfield.below-min", 400),
  ABOVE_MAX("customfield.above-max", 400),
  TOO_MANY_DECIMALS("customfield.too-many-decimals", 400),
  INVALID_DATE("customfield.invalid-date", 400),
  DATE_TOO_EARLY("customfield.date-too-early", 400),
  DATE_TOO_LATE("customfield.date-too-late", 400),
  INVALID_OPTION("customfield.invalid-option", 400),
  TOO_FEW_OPTIONS("customfield.too-few-options", 400),
  TOO_MANY_OPTIONS("customfield.too-many-options", 400),
  DUPLICATE_OPTION("customfield.duplicate-option", 400),
  UNKNOWN_FIELD("customfield.unknown-field", 400),
  INACTIVE_FIELD("customfield.inactive-field", 400);

  private final String code;
  private final int status;

  CustomFieldError(String code, int status) {
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
