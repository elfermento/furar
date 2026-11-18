// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** Acender ou apagar os datos abertos (Concello › Xeral, ADR-0034). */
public record UpdateOpenDataCommand(long expectedVersion, boolean enabled) {}
