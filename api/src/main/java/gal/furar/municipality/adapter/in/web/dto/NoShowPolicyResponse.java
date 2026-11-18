// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

/**
 * Penalización das faltas do concello (tarxeta «Non presentados», ADR-0032): ao chegar a {@code
 * threshold} faltas en {@code windowDays} días, {@code blockDays} días sen reservar por internet.
 * Apagada, os números quedan gardados pero non se aplican.
 */
public record NoShowPolicyResponse(boolean enabled, int threshold, int windowDays, int blockDays) {}
