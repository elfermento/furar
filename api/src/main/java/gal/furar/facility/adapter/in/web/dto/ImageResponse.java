// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Unha imaxe dunha instalación, sen bytes: foto da biblioteca ({@code id} = UUID) ou debuxo de
 * Furar ({@code id} = código). {@code large} (ata 1600 px) e {@code thumb} (480 px) son as rutas
 * públicas dos bytes. {@code uploadedAt} só nas fotos; {@code name} só nos debuxos.
 */
public record ImageResponse(
    ImageKindValue kind,
    String id,
    PhotoAltResponse alt,
    int width,
    int height,
    String large,
    String thumb,
    @Nullable Instant uploadedAt,
    @Nullable DrawingNameResponse name) {}
