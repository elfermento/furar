// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Identificador do concello no URL ({@code /naron}). Inmutable; 2–40 caracteres; minúsculas,
 * díxitos e guións; sen palabras reservadas (ADR-0006).
 */
public record Slug(String value) {

  public static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  public static final Set<String> RESERVED =
      Set.of(
          "api",
          "auth",
          "assets",
          "plataforma",
          "xestion",
          "admin",
          "healthz",
          "actuator",
          "codigo-fonte",
          "erro",
          "tiles",
          "en",
          "es",
          "gl");

  public Slug {
    if (value == null
        || value.length() < 2
        || value.length() > 40
        || !PATTERN.matcher(value).matches()) {
      throw new DomainException(
          MunicipalityError.SLUG_INVALID, Map.of("slug", String.valueOf(value)));
    }
    if (RESERVED.contains(value)) {
      throw new DomainException(MunicipalityError.SLUG_RESERVED, Map.of("slug", value));
    }
  }
}
