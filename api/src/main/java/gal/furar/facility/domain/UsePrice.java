// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Prezo dun uso (plan do dominio 2, §2.1; ADR-0025, punto 1): por franxa e, nos usos por prazas,
 * por persoa e franxa. Só o de pago leva importe, de 1 céntimo a 1 000 euros. Sen comprobar: {@link
 * FacilityUse} chama a {@link #violations()} ao crear ou editar o uso.
 *
 * @param kind tipo de prezo
 * @param amountCents importe en céntimos, só en {@link UsePriceKind#PAID}
 */
public record UsePrice(UsePriceKind kind, @Nullable Integer amountCents) {

  public static final int AMOUNT_MIN = 1;
  public static final int AMOUNT_MAX = 100_000;

  /** Sen prezo: o valor dun uso novo. */
  public static final UsePrice NONE = new UsePrice(UsePriceKind.NONE, null);

  public UsePrice {
    Objects.requireNonNull(kind, "kind");
  }

  /** Os erros por campo ({@code price.*}); baleira se vale. */
  public List<FieldViolation> violations() {
    List<FieldViolation> out = new ArrayList<>();
    if (kind == UsePriceKind.PAID) {
      if (amountCents == null) {
        out.add(FieldViolation.of("price.amountCents", ValidationError.REQUIRED));
      } else if (amountCents < AMOUNT_MIN || amountCents > AMOUNT_MAX) {
        out.add(
            FieldViolation.of(
                "price.amountCents",
                ValidationError.RANGE,
                Map.of("min", AMOUNT_MIN, "max", AMOUNT_MAX)));
      }
    } else if (amountCents != null) {
      out.add(FieldViolation.of("price.amountCents", ValidationError.INVALID));
    }
    return out;
  }
}
