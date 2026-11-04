// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.ErrorCode;

/** Erros do módulo concello. O 404 é {@code municipality.not-found} (TenancyError). */
public enum MunicipalityError implements ErrorCode {
  SLUG_TAKEN("municipality.slug-taken", 409),
  SLUG_INVALID("municipality.slug-invalid", 422),
  SLUG_RESERVED("municipality.slug-reserved", 422),
  NAME_INVALID("municipality.name-invalid", 422),
  LANGUAGES_INVALID("municipality.languages-invalid", 422),
  LANGUAGE_REQUIRED("municipality.language-required", 422),
  THEME_COLOR_INVALID("municipality.theme-color-invalid", 422),
  THEME_LOW_CONTRAST("municipality.theme-low-contrast", 422);

  private final String code;
  private final int status;

  MunicipalityError(String code, int status) {
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
