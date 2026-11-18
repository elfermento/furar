// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** «Buscar xente para xogar» enteiro (Concello › Xeral, ADR-0040). */
public record UpdateOpenMatchPolicyCommand(
    long expectedVersion, boolean enabled, int cancelHours) {}
