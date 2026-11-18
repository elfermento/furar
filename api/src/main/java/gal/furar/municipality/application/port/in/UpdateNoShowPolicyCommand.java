// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** Penalización das faltas enteira (tarxeta «Non presentados», ADR-0032). */
public record UpdateNoShowPolicyCommand(
    long expectedVersion, boolean enabled, int threshold, int windowDays, int blockDays) {}
