// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;

/**
 * As partidas abertas dunha páxina de concellos da portada común (ADR-0040): só os concellos coa
 * función acesa e algunha partida con sitio. Hai máis páxinas mentres {@code hasNext} sexa
 * verdadeiro.
 */
public record CatalogOpenMatchesResponse(
    int page,
    int size,
    boolean hasNext,
    List<CatalogOpenMatchesMunicipalityResponse> municipalities) {}
