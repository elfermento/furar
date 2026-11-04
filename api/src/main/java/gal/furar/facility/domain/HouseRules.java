// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Normas da instalación en texto libre, en gl e es (plan do dominio 2, P9; maqueta 06): as mesmas
 * regras ca a descrición. Opcionais, pero se hai, van nos dous idiomas.
 */
public record HouseRules(String gl, String es) {

  public static final int MAX = 2000;

  public HouseRules {
    Objects.requireNonNull(gl, "gl");
    Objects.requireNonNull(es, "es");
  }

  /**
   * @return nulo se os dous textos están baleiros (sen normas)
   */
  public static @Nullable HouseRules of(@Nullable String gl, @Nullable String es) {
    String g = strip(gl);
    String e = strip(es);
    if (g == null && e == null) {
      return null;
    }
    List<FieldViolation> invalid = new ArrayList<>();
    check(invalid, "houseRules.gl", g);
    check(invalid, "houseRules.es", e);
    if (!invalid.isEmpty()) {
      throw new DomainException(FacilityError.HOUSE_RULES_INVALID, Map.of("max", MAX), invalid);
    }
    return new HouseRules(g, e);
  }

  private static void check(List<FieldViolation> out, String field, @Nullable String value) {
    if (value == null) {
      out.add(FieldViolation.of(field, ValidationError.REQUIRED));
    } else if (value.length() > MAX) {
      out.add(FieldViolation.of(field, ValidationError.SIZE, Map.of("min", 1, "max", MAX)));
    }
  }

  private static @Nullable String strip(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
