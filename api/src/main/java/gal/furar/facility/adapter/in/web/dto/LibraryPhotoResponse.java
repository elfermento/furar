// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha foto da biblioteca do concello. {@code pending}: acabada de subir, aínda sen gardar en
 * ningunha instalación (sen {@code alt}). {@code usedIn}: nomes das instalacións que a usan.
 */
public record LibraryPhotoResponse(
    UUID id,
    @Nullable PhotoAltResponse alt,
    int width,
    int height,
    Instant uploadedAt,
    String large,
    String thumb,
    boolean pending,
    List<String> usedIn) {}
