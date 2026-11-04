// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Uso enteiro, para crealo ou editalo (con {@code If-Match} do uso).
 *
 * @param rentalMode {@code WHOLE}, {@code PARTS} (con 2 partes ou máis) ou {@code SEATS} (con
 *     {@code capacity} de 2 a 10 000)
 * @param slotMinutes duración de cada reserva: 30, 60, 90 ou 120
 * @param availability {@code ALWAYS} (sen {@code periods}) ou {@code DATES} (cun período polo
 *     menos)
 * @param blocksOthers só con {@code DATES}: nesas datas só se reserva este uso
 * @param parts as partes, enteiras e na súa orde (só con {@code PARTS})
 * @param weekdays días da semana nos que se reserva, máscara luns = 1 … domingo = 64 (sen el, 127:
 *     todos)
 * @param price prezo por franxa (sen el, sen prezo)
 * @param rules as catro normas (sen elas, ningunha)
 * @param decisions primeiro paso sen elas; se volve 409 {@code facility.affected-bookings}, a mesma
 *     petición cunha decisión por reserva de {@code affected[]}
 */
public record SaveUseRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull RentalModeValue rentalMode,
    @Nullable Integer capacity,
    @NotNull Integer slotMinutes,
    @NotNull UseAvailabilityValue availability,
    @Nullable Boolean blocksOthers,
    @Nullable @Size(max = 20) List<@Valid @NotNull UsePartRequest> parts,
    @Nullable @Size(max = 50) List<@Valid @NotNull UsePeriodRequest> periods,
    @Nullable Integer weekdays,
    @Nullable @Valid UsePriceRequest price,
    @Nullable @Valid UseRulesRequest rules,
    @Nullable @Size(max = 10000) List<@Valid @NotNull BookingDecisionRequest> decisions) {}
