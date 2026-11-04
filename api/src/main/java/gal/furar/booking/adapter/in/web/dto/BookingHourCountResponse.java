// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * Cantas reservas confirmadas empezan ese día e hora (Europe/Madrid).
 *
 * @param hour de 0 a 23
 */
public record BookingHourCountResponse(LocalDate date, int hour, long count) {}
