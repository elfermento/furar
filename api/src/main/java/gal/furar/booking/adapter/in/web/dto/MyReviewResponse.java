// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A valoración que acaba de deixar a persoa. */
public record MyReviewResponse(int stars, @Nullable String comment, Instant createdAt) {}
