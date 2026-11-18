// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Erro de negocio con código traducible. A capa web convérteo en {@code application/problem+json}
 * co status do {@link ErrorCode}.
 */
public class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient ErrorCode errorCode;
  private final transient Map<String, Object> params;
  private final transient List<FieldViolation> violations;

  public DomainException(ErrorCode errorCode) {
    this(errorCode, Map.of(), List.of());
  }

  public DomainException(ErrorCode errorCode, Map<String, Object> params) {
    this(errorCode, params, List.of());
  }

  public DomainException(
      ErrorCode errorCode, Map<String, Object> params, List<FieldViolation> violations) {
    super(Objects.requireNonNull(errorCode, "errorCode").code());
    this.errorCode = errorCode;
    this.params = params == null ? Map.of() : Map.copyOf(params);
    this.violations = violations == null ? List.of() : List.copyOf(violations);
  }

  /** Erro de validación ({@code validation.failed}) cos erros por campo. */
  public static DomainException validation(List<FieldViolation> violations) {
    return new DomainException(CommonError.VALIDATION_FAILED, Map.of(), violations);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public Map<String, Object> params() {
    return params;
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}
