// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Referencia a un deporte: un do catálogo de Furar (polo código) ou un creado polo concello (polo
 * id), nunca os dous. Que exista compróbao a base (FK); a mesma referencia dúas veces nunha
 * instalación, os seus índices parciais.
 */
public record SportRef(@Nullable String code, @Nullable CustomSportId customSportId) {

  public SportRef {
    if ((code == null) == (customSportId == null)) {
      throw new IllegalArgumentException("SportRef needs a code or a custom sport, not both");
    }
  }

  public static SportRef catalog(String code) {
    return new SportRef(Objects.requireNonNull(code, "code"), null);
  }

  public static SportRef custom(UUID id) {
    return new SportRef(null, new CustomSportId(id));
  }

  public boolean isCustom() {
    return customSportId != null;
  }
}
