// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Prezo do uso, por franxa (nas prazas, por persoa e franxa).
 *
 * @param amountCents só con {@code PAID}: de 1 a 100 000 céntimos
 */
public record UsePriceRequest(@NotNull PriceKindValue kind, @Nullable Integer amountCents) {}
