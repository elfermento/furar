// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha incidencia para o persoal. Na lista, sen {@code booking}.
 *
 * @param facilityName nome da instalación; baleiro se xa non existe
 * @param reporter quen avisou; nome e correo nulos se está anonimizada
 * @param photosRemoved se a baixa da conta de quen avisou xa borrou as fotos
 * @param booking a reserva da que vén o aviso, só no detalle
 * @param version para o {@code If-Match} de «Gardar»
 */
public record IncidentResponse(
    UUID id,
    UUID facilityId,
    String facilityName,
    IncidentTypeValue type,
    IncidentStatusValue status,
    IncidentOriginValue origin,
    String description,
    IncidentReporterResponse reporter,
    @Nullable String internalNote,
    @Nullable String resolutionMessage,
    List<IncidentPhotoResponse> photos,
    boolean photosRemoved,
    Instant createdAt,
    @Nullable Instant resolvedAt,
    @Nullable IncidentBookingResponse booking,
    long version) {

  /**
   * Quen avisou.
   *
   * @param anonymized sen persoa (baixa da conta)
   */
  public record IncidentReporterResponse(
      @Nullable String name, @Nullable String email, boolean anonymized) {}

  /** Unha foto: os bytes en {@code …/photos/{id}/large} e {@code …/thumb}, con sesión. */
  public record IncidentPhotoResponse(UUID id, int width, int height) {}

  /** A reserva da que vén o aviso. */
  public record IncidentBookingResponse(
      UUID id, String code, Instant start, Instant end, String useName) {}
}
