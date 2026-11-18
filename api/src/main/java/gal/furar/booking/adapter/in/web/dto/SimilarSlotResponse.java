// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un oco parecido ao pedido (ADR-0033, punto 9).
 *
 * @param facilityAddress rúa e número, só se é outra instalación
 * @param date o día en Europe/Madrid
 * @param end fin das franxas pedidas
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439)
 */
public record SimilarSlotResponse(
    SimilarSlotReasonValue reason,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
    UUID useId,
    LocalDate date,
    Instant start,
    Instant end,
    int startMinute) {}
