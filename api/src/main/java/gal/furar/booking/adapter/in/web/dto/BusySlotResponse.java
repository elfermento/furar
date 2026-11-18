// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;

/**
 * Unha hora ocupada (ADR-0033, punto 2): só que está ocupada, sen quen, sen cantas reservas, sen
 * parte e sen o motivo.
 *
 * @param start inicio real
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439)
 */
public record BusySlotResponse(Instant start, int startMinute) {}
