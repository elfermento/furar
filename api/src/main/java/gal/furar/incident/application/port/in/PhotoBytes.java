// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import java.util.Objects;

/** Os bytes dunha foto dunha incidencia e o seu tipo. O array non se copia. */
public record PhotoBytes(byte[] bytes, String contentType) {

  public PhotoBytes {
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(contentType, "contentType");
  }
}
