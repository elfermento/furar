// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import gal.furar.incident.domain.Incident;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha incidencia para o persoal: o agregado, o nome da instalación e, se vén dunha reserva, a
 * reserva (só no detalle).
 *
 * @param facilityName nome da instalación; baleiro se xa non existe
 */
public record IncidentView(
    Incident incident, String facilityName, @Nullable LinkedBooking booking) {

  public IncidentView {
    Objects.requireNonNull(incident, "incident");
    Objects.requireNonNull(facilityName, "facilityName");
  }

  /**
   * A reserva da que vén o aviso.
   *
   * @param useName o uso reservado; baleiro se xa non existe
   */
  public record LinkedBooking(UUID id, String code, Instant start, Instant end, String useName) {}
}
