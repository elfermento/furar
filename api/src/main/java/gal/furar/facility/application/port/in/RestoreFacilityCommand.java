// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.UseSpec;
import gal.furar.facility.domain.schedule.BlockSpec;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import gal.furar.facility.domain.schedule.SeasonSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Fila da sección facility dun ficheiro furar-export: a instalación co seu id, estado, datos, datas
 * e campos propios, e todo o que colga dela (fotos sen bytes, usos e partes arquivadas, tempadas,
 * excepcións e bloqueos), cos seus ids e datas. Unha fila anterior a D1-18 trae as listas baleiras.
 * Un borrador sen publicar pode vir sen enderezo (todo nulo) e calquera, sen punto no mapa.
 */
public record RestoreFacilityCommand(
    UUID id,
    String name,
    String status,
    @Nullable String street,
    @Nullable String streetNumber,
    @Nullable String postalCode,
    @Nullable String locality,
    @Nullable String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude,
    boolean published,
    DetailsInput details,
    @Nullable DescriptionInput description,
    @Nullable DescriptionInput houseRules,
    Set<Amenity> amenities,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> customFields,
    List<RestoreImage> images,
    List<RestoreUse> uses,
    List<ArchivedUsePart> archivedParts,
    List<RestoreSeason> seasons,
    List<RestoreException> exceptions,
    List<RestoreBlock> blocks) {

  public RestoreFacilityCommand {
    amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
    customFields = customFields == null ? Map.of() : customFields;
    images = images == null ? List.of() : List.copyOf(images);
    uses = uses == null ? List.of() : List.copyOf(uses);
    archivedParts = archivedParts == null ? List.of() : List.copyOf(archivedParts);
    seasons = seasons == null ? List.of() : List.copyOf(seasons);
    exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    blocks = blocks == null ? List.of() : List.copyOf(blocks);
  }

  /**
   * Unha imaxe da lista, na orde da galería (a primeira é a portada): un debuxo polo código ou unha
   * foto da biblioteca co seu metadato. Sen {@code createdAt}, a data da importación.
   */
  public record RestoreImage(
      @Nullable UUID photoId,
      @Nullable String drawing,
      @Nullable String altGl,
      @Nullable String altEs,
      @Nullable Integer width,
      @Nullable Integer height,
      @Nullable Instant createdAt) {}

  /** Uso co seu id, as súas partes vivas (co seu id) e, se o está, cando se arquivou. */
  public record RestoreUse(
      UUID id, UseSpec spec, @Nullable Instant archivedAt, Instant createdAt, Instant updatedAt) {}

  /** Tempada coa súa semana enteira. */
  public record RestoreSeason(
      UUID id, SeasonSpec spec, List<DayHoursSpec> week, Instant createdAt, Instant updatedAt) {}

  public record RestoreException(
      UUID id, ExceptionSpec spec, Instant createdAt, Instant updatedAt) {}

  /** Bloqueo con quen o creou; o período real calcúlase de novo desde a data e os minutos. */
  public record RestoreBlock(
      UUID id, BlockSpec spec, String createdBy, Instant createdAt, Instant updatedAt) {}
}
