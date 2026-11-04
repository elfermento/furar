// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.domain.ValidationError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha foto da biblioteca do concello (especificación, bloque 1, «Fotos»): reutilízase en calquera
 * das súas instalacións, co seu texto alternativo en gl e es (o mesmo en todas, P25), as medidas da
 * variante grande e o tipo. Recén subida está **pendente** ({@code pendingUntil}): non se ve na
 * biblioteca nin ten aínda texto alternativo ata que o «Gardar» dunha instalación a usa; se ninguén
 * a garda, bórrase pasado ese instante.
 */
public record Photo(
    PhotoId id,
    @Nullable LocalizedText alt,
    int width,
    int height,
    String contentType,
    Instant createdAt,
    @Nullable Instant pendingUntil) {

  public static final int ALT_MAX = 250;

  public Photo {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(createdAt, "createdAt");
    if (alt == null && pendingUntil == null) {
      throw new IllegalArgumentException("a library photo needs its alt text");
    }
  }

  /** Recén subida: pendente ata {@code until}, sen texto alternativo. */
  public static Photo uploaded(
      PhotoId id, int width, int height, String contentType, Instant createdAt, Instant until) {
    return new Photo(id, null, width, height, contentType, createdAt, until);
  }

  public boolean pending() {
    return pendingUntil != null;
  }

  /** Na biblioteca, co texto alternativo dado (deixa de estar pendente). */
  public Photo keep(LocalizedText newAlt) {
    return new Photo(id, newAlt, width, height, contentType, createdAt, null);
  }

  /**
   * Texto alternativo validado. {@code field} é o prefixo dos erros por campo ({@code alt}, {@code
   * images[2].alt}).
   */
  public static LocalizedText alt(String field, @Nullable String gl, @Nullable String es) {
    List<FieldViolation> invalid = new ArrayList<>();
    check(invalid, field + ".gl", gl);
    check(invalid, field + ".es", es);
    if (!invalid.isEmpty()) {
      throw new DomainException(FacilityError.PHOTO_ALT_INVALID, Map.of("max", ALT_MAX), invalid);
    }
    return new LocalizedText(gl, es);
  }

  private static void check(List<FieldViolation> out, String field, @Nullable String value) {
    if (value == null || value.isBlank()) {
      out.add(FieldViolation.of(field, ValidationError.REQUIRED));
    } else if (value.strip().length() > ALT_MAX) {
      out.add(FieldViolation.of(field, ValidationError.SIZE, Map.of("min", 1, "max", ALT_MAX)));
    }
  }
}
