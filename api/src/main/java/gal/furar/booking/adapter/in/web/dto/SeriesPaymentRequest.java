// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** O pago da tempada enteira (ADR-0039, punto 11). */
public record SeriesPaymentRequest(@NotNull SeriesPaymentStatusValue status) {}
