// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import java.util.Map;

/** Versión do recurso como {@code ETag} e lectura de {@code If-Match} (ADR-0009, ADR-0010). */
public final class ETags {

  private ETags() {}

  public static String of(long version) {
    return "\"" + version + "\"";
  }

  /**
   * Le a versión esperada de {@code If-Match}. Sen cabeceira → 428; mal formada ou {@code *} → 412.
   */
  public static long parseIfMatch(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new DomainException(CommonError.PRECONDITION_REQUIRED);
    }
    String value = ifMatch.strip();
    if (value.startsWith("W/")) {
      value = value.substring(2);
    }
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new DomainException(CommonError.STALE_VERSION, Map.of("ifMatch", ifMatch));
    }
  }
}
