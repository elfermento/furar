// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Tema do concello: cores que a web aplica como variables CSS. Garante contraste WCAG 2.1 AA: texto
 * 4,5:1 e elementos de interface 3:1.
 */
public record Theme(
    HexColor primary, HexColor onPrimary, HexColor accent, HexColor background, HexColor text) {

  public static final double TEXT_CONTRAST = 4.5;
  public static final double UI_CONTRAST = 3.0;

  /** Tema neutro por defecto (azul escuro sobre branco). */
  public static final Theme DEFAULT =
      new Theme(
          new HexColor("#0b4f8a"),
          new HexColor("#ffffff"),
          new HexColor("#b3541e"),
          new HexColor("#ffffff"),
          new HexColor("#1a1a1a"));

  public Theme {
    Objects.requireNonNull(primary, "primary");
    Objects.requireNonNull(onPrimary, "onPrimary");
    Objects.requireNonNull(accent, "accent");
    Objects.requireNonNull(background, "background");
    Objects.requireNonNull(text, "text");
    require("text/background", text, background, TEXT_CONTRAST);
    require("onPrimary/primary", onPrimary, primary, TEXT_CONTRAST);
    require("primary/background", primary, background, UI_CONTRAST);
    require("accent/background", accent, background, UI_CONTRAST);
  }

  private static void require(String pair, HexColor a, HexColor b, double minimum) {
    double ratio = a.contrastWith(b);
    if (ratio < minimum) {
      throw new DomainException(
          MunicipalityError.THEME_LOW_CONTRAST,
          Map.of(
              "pair",
              pair,
              "ratio",
              String.format(Locale.ROOT, "%.2f", ratio),
              "minimum",
              String.format(Locale.ROOT, "%.1f", minimum)));
    }
  }
}
