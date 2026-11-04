// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Marcar o cobro (§1.2.12, P13): {@code PAID} desde pendente, {@code REFUNDED} desde a devolver, e
 * desfacer ({@code PENDING} desde pagada, {@code TO_REFUND} desde devolta).
 */
public record ChangeBookingPaymentRequest(@NotNull PaymentStatusValue status) {}
