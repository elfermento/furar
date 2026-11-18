// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Ler o código dunha reserva para validala (ADR-0032, punto 4).
 *
 * @param code o código {@code FUR-XXXX}, o mesmo do QR
 * @param force «Validar igualmente» fóra da marxe
 * @param reason o motivo de validala fóra da marxe, obrigatorio con {@code force}
 */
public record ValidateBookingRequest(
    @NotBlank @Size(max = 20) String code,
    @Nullable Boolean force,
    @Nullable @Size(max = 200) String reason) {}
