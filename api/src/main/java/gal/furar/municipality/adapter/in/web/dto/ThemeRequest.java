// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cores {@code #rrggbb}. A API comproba o contraste WCAG AA. */
public record ThemeRequest(
    @NotNull @Pattern(regexp = ThemeRequest.HEX) String primary,
    @NotNull @Pattern(regexp = ThemeRequest.HEX) String onPrimary,
    @NotNull @Pattern(regexp = ThemeRequest.HEX) String accent,
    @NotNull @Pattern(regexp = ThemeRequest.HEX) String background,
    @NotNull @Pattern(regexp = ThemeRequest.HEX) String text) {

  public static final String HEX = "^#[0-9a-fA-F]{6}$";
}
