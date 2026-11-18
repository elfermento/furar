// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;

/**
 * O último correo que saíu dunha reserva («Confirmación enviada hoxe ás 10:42»).
 *
 * @param kind que correo
 * @param sentAt cando saíu
 */
public record BookingMailResponse(BookingMailKindValue kind, Instant sentAt) {}
