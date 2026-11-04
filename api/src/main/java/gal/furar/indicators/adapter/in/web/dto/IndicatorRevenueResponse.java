// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.UUID;

/** Os cobros das reservas de pago dunha instalación no período, en céntimos. */
public record IndicatorRevenueResponse(
    UUID facilityId,
    String name,
    long paidCents,
    long pendingCents,
    long toRefundCents,
    long refundedCents) {}
