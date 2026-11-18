// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.Objects;

/**
 * Unha foto xa tratada por {@link PhotoSanitizer}: os JPEG sen metadatos da variante grande (ata
 * 1600 px no lado longo) e da miniatura (ata 480 px) e as medidas da grande. Os arrays non se
 * copian: quen o recibe non os cambia.
 */
public record SanitizedPhoto(
    byte[] large, byte[] thumb, String contentType, int width, int height) {

  public SanitizedPhoto {
    Objects.requireNonNull(large, "large");
    Objects.requireNonNull(thumb, "thumb");
    Objects.requireNonNull(contentType, "contentType");
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("photo without pixels");
    }
  }
}
