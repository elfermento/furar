// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * Pago e devolución da instalación, se os ten (nulos = sen texto). Só se usan se o concello escolle
 * «Distinto por instalación» ({@code GET …/payment-notes}, {@code scope = FACILITY}); entón, sen
 * texto, vale o de Furar.
 */
public record FacilityPaymentNotesResponse(
    @Nullable FacilityNoteResponse payment, @Nullable FacilityNoteResponse refund) {}
