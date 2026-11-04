// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

/** Erros comúns a todos os módulos. */
public enum CommonError implements ErrorCode {
  VALIDATION_FAILED("validation.failed", 400),
  MALFORMED_JSON("common.malformed-json", 400),
  INVALID_PARAMETER("common.invalid-parameter", 400),
  NOT_FOUND("common.not-found", 404),
  METHOD_NOT_ALLOWED("common.method-not-allowed", 405),
  NOT_ACCEPTABLE("common.not-acceptable", 406),
  STALE_VERSION("common.stale-version", 412),
  UNSUPPORTED_MEDIA_TYPE("common.unsupported-media-type", 415),
  PRECONDITION_REQUIRED("common.precondition-required", 428),
  /** Demasiadas peticións desde o mesmo enderezo nun minuto ({@code RateLimitFilter}). */
  RATE_LIMITED("common.rate-limited", 429),
  INTERNAL("common.internal", 500),
  /**
   * Outra escritura ten o mesmo recurso bloqueado máis do tempo de espera ({@code lock_timeout}).
   */
  BUSY("common.busy", 503);

  private final String code;
  private final int status;

  CommonError(String code, int status) {
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
