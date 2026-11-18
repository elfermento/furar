// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;

/**
 * Un concello da portada común (ADR-0030) coas instalacións que ve a súa veciñanza, na orde
 * alfabética do idioma, e se publica datos abertos (ADR-0034). As rutas das imaxes xa levan o seu
 * concello.
 */
public record CatalogMunicipalityResponse(
    String slug, String name, boolean openData, List<PublicFacilityResponse> facilities) {}
