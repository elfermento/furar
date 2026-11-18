// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** O pago que marca o persoal. */
public record EventPaymentRequest(@NotNull EventPaymentStatusValue status) {}
