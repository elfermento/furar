// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Tarxeta do listado: nome, enderezo, estado, deportes, imaxe de portada (a primeira ou o debuxo
 * suxerido), se está publicada, o que falta e a {@code version} para cambiar o estado desde o chip
 * con {@code If-Match} (ADR-0022, punto 7).
 */
public record FacilitySummaryResponse(
    UUID id,
    String name,
    FacilityStatusValue status,
    @Nullable AddressResponse address,
    boolean published,
    List<SportResponse> sports,
    @Nullable ImageResponse cover,
    List<RequirementValue> missing,
    long version) {}
