// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Cor {@code #rrggbb} con luminancia relativa WCAG 2.1. */
public record HexColor(String value) {

  private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

  public HexColor {
    if (value == null || !HEX.matcher(value).matches()) {
      throw new DomainException(
          MunicipalityError.THEME_COLOR_INVALID, Map.of("color", String.valueOf(value)));
    }
    value = value.toLowerCase(Locale.ROOT);
  }

  /** Luminancia relativa (WCAG 2.1, 1.4.3). */
  public double luminance() {
    int rgb = Integer.parseInt(value.substring(1), 16);
    return 0.2126 * channel(rgb >> 16 & 0xff)
        + 0.7152 * channel(rgb >> 8 & 0xff)
        + 0.0722 * channel(rgb & 0xff);
  }

  /** Contraste con outra cor, de 1 a 21. */
  public double contrastWith(HexColor other) {
    double a = luminance();
    double b = other.luminance();
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }

  private static double channel(int c) {
    double s = c / 255.0;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  }
}
