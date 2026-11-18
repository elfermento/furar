// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * O total conxelado ao reservar (importe por franxa × franxas).
 *
 * @param amountCents só con {@code PAID}
 */
public record BookingPriceResponse(BookingPriceKindValue kind, @Nullable Integer amountCents) {}
