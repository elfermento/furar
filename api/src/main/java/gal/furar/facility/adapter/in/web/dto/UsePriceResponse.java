// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * Prezo do uso por franxa. {@code amountCents} só con {@code PAID}; {@code per} sae do modo do uso
 * ({@code PERSON} nos usos por prazas).
 */
public record UsePriceResponse(
    PriceKindValue kind, @Nullable Integer amountCents, PricePerValue per) {}
