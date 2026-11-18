// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

/** Un debuxo de Furar (catálogo da plataforma, CC0), coas rutas públicas dos bytes. */
public record DrawingResponse(
    String code,
    DrawingNameResponse name,
    PhotoAltResponse alt,
    int width,
    int height,
    String large,
    String thumb) {}
