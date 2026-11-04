// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.portability.dto;

import gal.furar.facility.adapter.in.web.dto.PhotoAltResponse;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha imaxe da instalación no ficheiro furar-export, na orde da galería (a primeira é a portada):
 * un debuxo polo código ({@code drawing}) ou unha foto da biblioteca co seu metadato e sen bytes
 * (ADR-0018, P32; os bytes van na copia da base). A mesma foto pode aparecer en varias filas.
 */
public record ImageExportResponse(
    @Nullable String drawing,
    @Nullable UUID photoId,
    @Nullable PhotoAltResponse alt,
    @Nullable Integer width,
    @Nullable Integer height,
    @Nullable Instant createdAt) {}
