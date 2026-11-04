// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;

/**
 * Unha pestana de «Cobros»: cantas hai e as primeiras.
 *
 * @param count o total, para a conta da pestana
 */
public record BookingTabResponse(long count, List<ManagedBookingResponse> items) {}
