// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;

/**
 * Unha páxina da portada común (ADR-0030): os concellos van por páxinas, por nome, e só saen os que
 * teñen os datos legais completos e algunha instalación activa á vista. {@code items} pode traer
 * menos concellos ca {@code size}; hai máis páxinas mentres {@code hasNext} sexa verdadeiro.
 */
public record FacilityCatalogResponse(
    int page, int size, boolean hasNext, List<CatalogMunicipalityResponse> items) {}
