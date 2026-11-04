// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import java.util.UUID;

/** Concello na lista. */
public record MunicipalitySummaryResponse(UUID id, String slug, String name) {}
