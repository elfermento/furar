// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A portada da instalación dunha reserva, coa mesma forma ca a da ficha pública: foto da biblioteca
 * ({@code id} = UUID) ou debuxo de Furar ({@code id} = código). {@code large} e {@code thumb} son
 * as rutas públicas dos bytes. {@code uploadedAt} só nas fotos; {@code name} só nos debuxos.
 */
public record CoverImageResponse(
    CoverKindValue kind,
    String id,
    CoverTextResponse alt,
    int width,
    int height,
    String large,
    String thumb,
    @Nullable Instant uploadedAt,
    @Nullable CoverTextResponse name) {}
