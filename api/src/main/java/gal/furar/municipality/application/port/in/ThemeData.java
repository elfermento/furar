// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** Cores do tema tal como chegan ({@code #rrggbb}). */
public record ThemeData(
    String primary, String onPrimary, String accent, String background, String text) {}
