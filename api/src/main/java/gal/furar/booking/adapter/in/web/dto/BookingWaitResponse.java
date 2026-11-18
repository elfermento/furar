// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha espera da persoa (ADR-0033, punto 6): a hora pola que pediu que se lle avise se queda libre,
 * cos nomes de agora da instalación e do uso.
 *
 * @param cover portada da instalación; nula se xa non existe
 */
public record BookingWaitResponse(
    UUID id,
    UUID facilityId,
    String facilityName,
    @Nullable CoverImageResponse cover,
    UUID useId,
    String useName,
    Instant start,
    Instant end) {}
