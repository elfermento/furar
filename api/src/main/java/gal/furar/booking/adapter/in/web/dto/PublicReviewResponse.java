// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha valoración en público: sen ningún dato de quen a deixou.
 *
 * @param comment o comentario, se o hai e o xestor non o ocultou
 */
public record PublicReviewResponse(
    UUID id, int stars, @Nullable String comment, Instant createdAt) {}
