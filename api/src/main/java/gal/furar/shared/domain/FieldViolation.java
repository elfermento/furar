// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Erro nun campo concreto ({@code errors[]} do problema RFC 9457).
 *
 * @param field ruta do campo, p. ex. {@code customFields.phone}
 * @param code código traducible
 * @param params valores para interpolar no texto
 */
public record FieldViolation(String field, String code, Map<String, Object> params) {

  public FieldViolation {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(code, "code");
    params = params == null ? Map.of() : Map.copyOf(params);
  }

  public static FieldViolation of(String field, ErrorCode code) {
    return new FieldViolation(field, code.code(), Map.of());
  }

  public static FieldViolation of(String field, ErrorCode code, Map<String, Object> params) {
    return new FieldViolation(field, code.code(), params);
  }
}
