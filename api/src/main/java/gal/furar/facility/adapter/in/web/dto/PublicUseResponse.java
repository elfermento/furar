// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un uso que a veciñanza pode reservar: nome, como se aluga (coas partes ou as prazas), canto dura
 * cada reserva, se só vale unhas datas os seus períodos, os días da semana (máscara luns = 1 …
 * domingo = 64), o prezo por franxa e as normas. Sen versión nin «bloquea os demais», que son da
 * xestión.
 */
public record PublicUseResponse(
    UUID id,
    String name,
    RentalModeValue rentalMode,
    List<UsePartResponse> parts,
    @Nullable Integer capacity,
    int slotMinutes,
    UseAvailabilityValue availability,
    List<UsePeriodResponse> periods,
    int weekdays,
    UsePriceResponse price,
    UseRulesResponse rules) {}
