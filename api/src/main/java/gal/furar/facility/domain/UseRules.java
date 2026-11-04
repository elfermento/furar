// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * As catro normas dun uso (plan do dominio 2, §2.1 e §2.3): baleira = non se aplica, e todas
 * baleiras por defecto. Aquí só se gardan e se comproban os rangos; avalíaas {@code booking} ao
 * reservar, cambiar e cancelar. Sen comprobar: {@link FacilityUse} chama a {@link #violations()}.
 *
 * @param minAdvanceHours antelación mínima, en horas (0–720)
 * @param maxAdvanceDays antelación máxima, en días (1–365)
 * @param maxSlots duración máxima, en franxas seguidas (1–16)
 * @param capCount límite por persoa: reservas por período (1–50), con {@code capPeriod}
 * @param capPeriod período natural do límite, con {@code capCount}
 * @param cancelHours cancelar sen custo ata estas horas antes do inicio (0–720)
 */
public record UseRules(
    @Nullable Integer minAdvanceHours,
    @Nullable Integer maxAdvanceDays,
    @Nullable Integer maxSlots,
    @Nullable Integer capCount,
    @Nullable UseCapPeriod capPeriod,
    @Nullable Integer cancelHours) {

  public static final int MIN_ADVANCE_HOURS_MAX = 720;
  public static final int MAX_ADVANCE_DAYS_MAX = 365;
  public static final int MAX_SLOTS_MAX = 16;
  public static final int CAP_COUNT_MAX = 50;
  public static final int CANCEL_HOURS_MAX = 720;

  /** Sen ningunha norma: o valor dun uso novo. */
  public static final UseRules NONE = new UseRules(null, null, null, null, null, null);

  /** Os erros por campo ({@code rules.*}); baleira se vale. */
  public List<FieldViolation> violations() {
    List<FieldViolation> out = new ArrayList<>();
    range(out, "minAdvanceHours", minAdvanceHours, 0, MIN_ADVANCE_HOURS_MAX);
    range(out, "maxAdvanceDays", maxAdvanceDays, 1, MAX_ADVANCE_DAYS_MAX);
    range(out, "maxSlots", maxSlots, 1, MAX_SLOTS_MAX);
    range(out, "capCount", capCount, 1, CAP_COUNT_MAX);
    range(out, "cancelHours", cancelHours, 0, CANCEL_HOURS_MAX);
    if (capCount != null && capPeriod == null) {
      out.add(FieldViolation.of("rules.capPeriod", ValidationError.REQUIRED));
    } else if (capCount == null && capPeriod != null) {
      out.add(FieldViolation.of("rules.capCount", ValidationError.REQUIRED));
    }
    return out;
  }

  private static void range(
      List<FieldViolation> out, String field, @Nullable Integer value, int min, int max) {
    if (value != null && (value < min || value > max)) {
      out.add(
          FieldViolation.of(
              "rules." + field, ValidationError.RANGE, Map.of("min", min, "max", max)));
    }
  }
}
