// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import gal.furar.incident.domain.IncidentType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un aviso: a instalación, a reserva se vén de «As miñas reservas» (a veciñanza), o tipo, o texto e
 * as fotos tal como se subiron (aínda sen tratar).
 */
public record ReportIncidentCommand(
    UUID facilityId,
    @Nullable UUID bookingId,
    IncidentType type,
    String description,
    List<byte[]> photos) {

  public ReportIncidentCommand {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(description, "description");
    photos = List.copyOf(photos);
  }
}
