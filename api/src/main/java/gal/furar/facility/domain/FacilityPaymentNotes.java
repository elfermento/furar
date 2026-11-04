// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Como se paga e como se devolve nesta instalación, en gl e es (D2-11, revisión do 2026-11-03). Só
 * se usan cando o concello escolle «Distinto por instalación»; daquela non herdan os do concello:
 * sen texto, vale o de Furar. Opcionais; se hai, nos dous idiomas.
 *
 * @param payment «Como se paga», ou nulo
 * @param refund «Como se devolve», ou nulo
 */
public record FacilityPaymentNotes(
    @Nullable LocalizedText payment, @Nullable LocalizedText refund) {

  public static final int MAX = LocalizedText.MAX_LENGTH;

  public static final FacilityPaymentNotes NONE = new FacilityPaymentNotes(null, null);

  /**
   * Comproba e normaliza: os dous textos dunha nota baleiros = sen nota; un só, erro por campo
   * ({@code paymentNotes.payment.gl}…).
   */
  public static FacilityPaymentNotes of(
      @Nullable String paymentGl,
      @Nullable String paymentEs,
      @Nullable String refundGl,
      @Nullable String refundEs) {
    List<FieldViolation> errors = new ArrayList<>();
    LocalizedText payment = note(errors, "payment", paymentGl, paymentEs);
    LocalizedText refund = note(errors, "refund", refundGl, refundEs);
    if (!errors.isEmpty()) {
      throw DomainException.validation(errors);
    }
    return new FacilityPaymentNotes(payment, refund);
  }

  private static @Nullable LocalizedText note(
      List<FieldViolation> errors, String field, @Nullable String gl, @Nullable String es) {
    String g = gl == null || gl.isBlank() ? null : gl.strip();
    String e = es == null || es.isBlank() ? null : es.strip();
    if (g == null && e == null) {
      return null;
    }
    int before = errors.size();
    check(errors, "paymentNotes." + field + ".gl", g);
    check(errors, "paymentNotes." + field + ".es", e);
    return errors.size() > before ? null : new LocalizedText(g, e);
  }

  private static void check(List<FieldViolation> errors, String field, @Nullable String value) {
    if (value == null) {
      errors.add(FieldViolation.of(field, ValidationError.REQUIRED));
    } else if (value.length() > MAX) {
      errors.add(FieldViolation.of(field, ValidationError.SIZE, Map.of("min", 1, "max", MAX)));
    }
  }
}
