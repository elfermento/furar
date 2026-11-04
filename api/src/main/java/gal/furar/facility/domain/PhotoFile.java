// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Objects;

/**
 * Bytes dunha variante xa reencodada (sen EXIF) e o seu tipo. Non se copia o array: quen o crea non
 * o volve tocar.
 */
public record PhotoFile(byte[] bytes, String contentType) {

  public PhotoFile {
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(contentType, "contentType");
  }
}
