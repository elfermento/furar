// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uso dunha instalación. {@code capacity} só con {@code SEATS}; {@code parts} só con {@code PARTS};
 * {@code periods} en orde, só con {@code DATES}. {@code weekdays}: máscara luns = 1 … domingo = 64.
 * {@code version} é a do {@code If-Match} do uso. Nun ficheiro furar-export anterior ao dominio 2
 * faltan {@code weekdays}, {@code price} e {@code rules}: impórtase todos os días, sen prezo nin
 * normas.
 */
public record UseResponse(
    UUID id,
    UUID facilityId,
    String name,
    RentalModeValue rentalMode,
    @Nullable Integer capacity,
    int slotMinutes,
    UseAvailabilityValue availability,
    boolean blocksOthers,
    List<UsePartResponse> parts,
    List<UsePeriodResponse> periods,
    Integer weekdays,
    UsePriceResponse price,
    UseRulesResponse rules,
    @Nullable Instant archivedAt,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
