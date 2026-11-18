// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * As celas dos CSV do proxecto (ADR-0026, punto 11; ADR-0034, punto 15): UTF-8 con BOM, {@code ;}
 * entre celas, CRLF, comiñas RFC 4180 e {@code '} diante do que unha folla de cálculo tomaría por
 * fórmula.
 */
public final class CsvCells {

  public static final String BOM = "﻿";

  public static final char SEPARATOR = ';';

  public static final String LINE_END = "\r\n";

  private static final Pattern FORMULA_START = Pattern.compile("^[=+\\-@\\t\\r]");
  private static final Pattern NEEDS_QUOTES = Pattern.compile("[\";\\r\\n]");

  private CsvCells() {}

  /** Texto: neutraliza as fórmulas e pon comiñas se fai falta; baleiro se non hai. */
  public static String text(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String safe = FORMULA_START.matcher(value).find() ? "'" + value : value;
    return NEEDS_QUOTES.matcher(safe).find() ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
  }

  /** Número con punto decimal e esa escala, sen agrupación de miles; baleiro se non hai. */
  public static String decimal(@Nullable BigDecimal value, int scale) {
    return value == null ? "" : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
  }

  /**
   * Unha lista nunha cela, separada por {@code |} sen espazos; un {@code |} ou un {@code \} dentro
   * dun valor escríbese {@code \|} ou {@code \\}.
   */
  public static String list(Collection<String> values) {
    return text(
        values.stream()
            .map(v -> v.replace("\\", "\\\\").replace("|", "\\|"))
            .collect(Collectors.joining("|")));
  }
}
