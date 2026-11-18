// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import java.util.UUID;

/**
 * Concello na lista.
 *
 * @param openData se publica datos abertos (ADR-0034): a raíz só liga «Datos abertos» se algún o
 *     fai
 */
public record MunicipalitySummaryResponse(UUID id, String slug, String name, boolean openData) {}
