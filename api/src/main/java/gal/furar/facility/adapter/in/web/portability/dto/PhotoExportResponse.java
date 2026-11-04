// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.portability.dto;

import gal.furar.facility.adapter.in.web.dto.PhotoAltResponse;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Metadato dunha foto no ficheiro furar-export, na orde da galería e sen bytes (ADR-0018, P32): os
 * bytes van na copia da base. {@code createdAt} pode faltar nun ficheiro anterior.
 */
public record PhotoExportResponse(
    UUID id,
    boolean cover,
    PhotoAltResponse alt,
    int width,
    int height,
    @Nullable Instant createdAt) {}
