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
 * Deporte creado polo concello con «+ crear» (especificación, bloque 1; P26: nome en gl e es,
 * porque a veciñanza busca por eles). O nome repetido sen maiúsculas (en gl) dío a base.
 */
public record CustomSport(CustomSportId id, LocalizedText label, Instant createdAt) {

  public static final int LABEL_MAX = 60;

  public CustomSport {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Alta: os dous nomes son obrigatorios e de ata {@value #LABEL_MAX} caracteres. */
  public static CustomSport create(
      CustomSportId id, @Nullable String gl, @Nullable String es, Instant now) {
    List<FieldViolation> invalid = new ArrayList<>();
    check(invalid, "label.gl", gl);
    check(invalid, "label.es", es);
    if (!invalid.isEmpty()) {
      throw new DomainException(
          FacilityError.SPORT_NAME_INVALID, Map.of("max", LABEL_MAX), invalid);
    }
    return new CustomSport(id, new LocalizedText(gl, es), now);
  }

  public Sport sport() {
    return new Sport(new SportRef(null, id), label);
  }

  private static void check(List<FieldViolation> out, String field, @Nullable String value) {
    if (value == null || value.isBlank()) {
      out.add(FieldViolation.of(field, ValidationError.REQUIRED));
    } else if (value.strip().length() > LABEL_MAX) {
      out.add(FieldViolation.of(field, ValidationError.SIZE, Map.of("min", 1, "max", LABEL_MAX)));
    }
  }
}
