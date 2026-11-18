// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** Datos abertos acesos ou apagados (con {@code If-Match}; ADR-0034, punto 12). */
public record OpenDataPublicationRequest(@NotNull Boolean enabled) {}
