// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha valoración para a xestión: co comentario aínda que estea oculto; nunca quen a deixou.
 *
 * @param facilityName nome de agora da instalación
 * @param useName nome de agora do uso
 * @param hidden se o comentario está oculto en público
 */
public record ManagedReviewResponse(
    UUID id,
    UUID facilityId,
    String facilityName,
    UUID useId,
    String useName,
    int stars,
    @Nullable String comment,
    boolean hidden,
    Instant createdAt) {}
