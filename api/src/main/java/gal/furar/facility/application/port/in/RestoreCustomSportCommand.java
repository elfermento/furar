// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Deporte do concello dun ficheiro furar-export, co seu id (as filas refírense a el). Sen {@code
 * createdAt} (fila dentro dunha instalación), a data da importación.
 */
public record RestoreCustomSportCommand(
    UUID id, String gl, String es, @Nullable Instant createdAt) {

  public RestoreCustomSportCommand(UUID id, String gl, String es) {
    this(id, gl, es, null);
  }
}
