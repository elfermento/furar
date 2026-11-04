// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Prezo e normas dun uso para outros módulos (ADR-0025, punto 1; A19: só tipos da raíz e de Java).
 * Cando chegue a herdanza concello → instalación → uso, serán «as efectivas» sen cambiar a firma.
 */
public record UseTerms(Price price, Rules rules) {

  public UseTerms {
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(rules, "rules");
  }

  /**
   * Prezo por franxa.
   *
   * @param kind {@code NONE} (sen prezo), {@code FREE} ou {@code PAID}
   * @param amountCents só con {@code PAID}
   * @param per {@code BOOKING} (por reserva da franxa) ou {@code PERSON} (usos por prazas)
   */
  public record Price(String kind, @Nullable Integer amountCents, String per) {}

  /**
   * As catro normas; nula = non se aplica (plan do dominio 2, §2.3).
   *
   * @param capPeriod {@code DAY}, {@code WEEK} ou {@code MONTH}, con {@code capCount}
   */
  public record Rules(
      @Nullable Integer minAdvanceHours,
      @Nullable Integer maxAdvanceDays,
      @Nullable Integer maxSlots,
      @Nullable Integer capCount,
      @Nullable String capPeriod,
      @Nullable Integer cancelHours) {}
}
