// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * O límite de idade dun evento polo ano de nacemento (ADR-0041): desde, ata, os dous ou ningún,
 * incluídos.
 */
public record BirthYears(@Nullable Integer from, @Nullable Integer to) {

  public static final int MIN = 1900;
  public static final int MAX = 2200;

  /** Sen límite. */
  public static final BirthYears NONE = new BirthYears(null, null);

  public BirthYears {
    if ((from != null && (from < MIN || from > MAX))
        || (to != null && (to < MIN || to > MAX))
        || (from != null && to != null && from > to)) {
      throw new DomainException(EventError.BIRTH_YEARS_INVALID, Map.of("min", MIN, "max", MAX));
    }
  }

  /** Se o evento ten límite de idade. */
  public boolean limited() {
    return from != null || to != null;
  }

  /** Se ese ano de nacemento entra. */
  public boolean allows(int year) {
    return (from == null || year >= from) && (to == null || year <= to);
  }

  /**
   * Comproba un ano de nacemento.
   *
   * @throws DomainException {@code event.age-not-allowed} se non entra
   */
  public void check(int year) {
    if (!allows(year)) {
      throw new DomainException(EventError.AGE_NOT_ALLOWED);
    }
  }
}
