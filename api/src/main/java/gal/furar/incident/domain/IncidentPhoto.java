// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Unha foto dunha incidencia, xa sen metadatos: a súa orde (0 a 2) e as medidas da grande. Os bytes
 * van á parte.
 */
public record IncidentPhoto(UUID id, int position, int width, int height) {

  public IncidentPhoto {
    Objects.requireNonNull(id, "id");
    if (position < 0 || position >= Incident.MAX_PHOTOS) {
      throw new IllegalArgumentException("photo position out of range: " + position);
    }
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("photo without pixels");
    }
  }
}
