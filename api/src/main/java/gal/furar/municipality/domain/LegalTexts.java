// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Textos propios das tres páxinas legais (ADR-0017 punto 8). Gárdanse aínda que a páxina non estea
 * no modo {@link LegalPageMode#TEXT}: cambiar de modo non os borra. Un texto sen ningún idioma é
 * nulo. Os erros de tamaño saen por campo ({@code legalTexts.privacy.gl}…).
 */
public record LegalTexts(
    @Nullable LegalText privacy,
    @Nullable LegalText accessibility,
    @Nullable LegalText legalNotice) {

  public static final LegalTexts EMPTY = new LegalTexts(null, null, null);

  public LegalTexts {
    privacy = emptyToNull(privacy);
    accessibility = emptyToNull(accessibility);
    legalNotice = emptyToNull(legalNotice);
    List<FieldViolation> errors = new ArrayList<>();
    check(errors, "privacy", privacy);
    check(errors, "accessibility", accessibility);
    check(errors, "legalNotice", legalNotice);
    if (!errors.isEmpty()) {
      throw DomainException.validation(errors);
    }
  }

  private static @Nullable LegalText emptyToNull(@Nullable LegalText text) {
    return text == null || text.isEmpty() ? null : text;
  }

  private static void check(List<FieldViolation> errors, String page, @Nullable LegalText text) {
    if (text == null) {
      return;
    }
    size(errors, "legalTexts." + page + ".gl", text.gl());
    size(errors, "legalTexts." + page + ".es", text.es());
  }

  private static void size(List<FieldViolation> errors, String field, @Nullable String value) {
    if (value != null && value.length() > LegalText.MAX) {
      errors.add(FieldViolation.of(field, ValidationError.SIZE, Map.of("max", LegalText.MAX)));
    }
  }
}
