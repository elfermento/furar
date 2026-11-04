// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.portability.dto;

import gal.furar.facility.adapter.in.web.dto.AddressResponse;
import gal.furar.facility.adapter.in.web.dto.AmenityValue;
import gal.furar.facility.adapter.in.web.dto.FacilityDescriptionResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityDetailsResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityStatusValue;
import gal.furar.facility.adapter.in.web.dto.HouseRulesResponse;
import gal.furar.facility.adapter.in.web.dto.RequirementValue;
import gal.furar.facility.adapter.in.web.dto.UseResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleExceptionResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.SeasonResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Fila da sección {@code facility} do ficheiro furar-export (ADR-0018): os campos da ficha ({@link
 * FacilityResponse}, cos campos propios todos, tamén os de persoal) e o que colga da instalación:
 * imaxes (debuxos e fotos da biblioteca sen bytes, P32), usos (tamén arquivados) e as súas partes
 * arquivadas, tempadas coa semana, excepcións e bloqueos. {@code missing} vai sempre baleiro
 * (calcúlase ao importar). As listas novas poden faltar nun ficheiro anterior: entón a instalación
 * entra sen elas. {@code photos} só se le (galería anterior ao 2026-10-26, coa portada marcada); a
 * exportación escribe {@code images}. {@code address} falta nun borrador que aínda non o ten;
 * {@code published} falta nun ficheiro anterior ao asistente «Nova instalación» (entón, publicada).
 */
public record FacilityExportResponse(
    UUID id,
    String name,
    FacilityStatusValue status,
    @Nullable AddressResponse address,
    @Nullable Boolean published,
    FacilityDetailsResponse details,
    @Nullable FacilityDescriptionResponse description,
    @Nullable HouseRulesResponse houseRules,
    List<AmenityValue> amenities,
    @Nullable List<PhotoExportResponse> photos,
    @Nullable List<ImageExportResponse> images,
    List<RequirementValue> missing,
    long version,
    Instant createdAt,
    Instant updatedAt,
    @Nullable List<UseResponse> uses,
    @Nullable List<ArchivedPartExportResponse> archivedParts,
    @Nullable List<SeasonResponse> seasons,
    @Nullable List<ScheduleExceptionResponse> exceptions,
    @Nullable List<BlockExportResponse> blocks) {}
