// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentStatus;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * «Gardar» no detalle: o estado, a nota interna e, se queda resolta, a mensaxe para a persoa.
 *
 * @param expectedVersion a versión lida ({@code If-Match})
 */
public record ReviewIncidentCommand(
    IncidentId id,
    long expectedVersion,
    IncidentStatus status,
    @Nullable String internalNote,
    @Nullable String resolutionMessage) {

  public ReviewIncidentCommand {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
  }
}
