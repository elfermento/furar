// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Objects;

/**
 * Unha imaxe subida despois de comprobala, endereitala, quitarlle o EXIF e reencodala: a variante
 * grande, a miniatura e as medidas da grande.
 */
public record ProcessedPhoto(PhotoFile large, PhotoFile thumb, int width, int height) {

  public ProcessedPhoto {
    Objects.requireNonNull(large, "large");
    Objects.requireNonNull(thumb, "thumb");
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("photo without pixels");
    }
  }

  public PhotoFile file(PhotoVariant variant) {
    return variant == PhotoVariant.LARGE ? large : thumb;
  }
}
