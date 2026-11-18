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
  THEME_LOW_CONTRAST("municipality.theme-low-contrast", 422),
  /** NIF cun DNI ou NIE: o dos datos legais é o da entidade ({@link EntityNif}). */
  NIF_PERSONAL("municipality.nif-personal", 400),
  /** NIF de entidade co carácter de control mal. */
  NIF_CHECK_DIGIT("municipality.nif-check-digit", 400),
  /** Os números da penalización das faltas fóra de rango (ADR-0032, punto 8). */
  NO_SHOW_POLICY_INVALID("municipality.no-show-policy-invalid", 422),
  /** «Cancelar se non hai mínimo» fóra de 1 a 72 horas (ADR-0040): {@code min} e {@code max}. */
  OPEN_MATCH_POLICY_INVALID("municipality.open-match-policy-invalid", 422);

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
