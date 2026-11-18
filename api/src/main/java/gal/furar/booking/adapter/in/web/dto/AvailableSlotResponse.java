// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un oco libre.
 *
 * @param start inicio real
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439), para pintalo sen
 *     calcular zonas
 * @param maxSlots cantas franxas seguidas se poden reservar desde aquí (≥ 1)
 * @param freeParts as partes libres, na súa orde; só nos usos por partes
 * @param freeSeats as prazas libres; só nos usos por prazas
 */
public record AvailableSlotResponse(
    Instant start,
    int startMinute,
    int maxSlots,
    @Nullable List<UUID> freeParts,
    @Nullable Integer freeSeats) {}
