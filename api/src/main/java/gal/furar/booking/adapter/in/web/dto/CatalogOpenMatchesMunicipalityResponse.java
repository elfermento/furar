// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;

/** Un concello da portada común coas súas partidas abertas, da que empeza antes. */
public record CatalogOpenMatchesMunicipalityResponse(
    String slug, String name, List<OpenMatchCardResponse> items) {}
