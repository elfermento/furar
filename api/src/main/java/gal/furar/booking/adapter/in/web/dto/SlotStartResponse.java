// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;

/** Unha hora de inicio: real e no reloxo de parede de Europe/Madrid (0–1439). */
public record SlotStartResponse(Instant start, int startMinute) {}
