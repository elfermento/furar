// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Textos do concello sobre como se paga e como se devolve (plan do dominio 2, P8), en gl e es. Nulo
 * = o texto por defecto de Furar ({@link #DEFAULT_PAYMENT}, {@link #DEFAULT_REFUND}); a web e o
 * correo nunca levan estes textos no código, saen sempre daquí. Co ámbito {@link
 * PaymentNotesScope#FACILITY} os textos do concello quedan gardados pero non se usan: cada
 * instalación leva os seus (revisión do 2026-11-03).
 *
 * @param scope se os textos valen para todas as instalacións ou van en cada ficha
 * @param payment «Como se paga», ou nulo
 * @param refund «Como se devolve», ou nulo
 */
public record PaymentNotes(
    PaymentNotesScope scope, @Nullable LocalizedText payment, @Nullable LocalizedText refund) {

  public static final int MAX = LocalizedText.MAX_LENGTH;

  /** Por defecto: pago no mostrador (fase 1, ADR-0025). */
  public static final LocalizedText DEFAULT_PAYMENT =
      new LocalizedText("No mostrador, ao chegar", "En el mostrador, al llegar");

  public static final LocalizedText DEFAULT_REFUND =
      new LocalizedText(
          "O concello dirache como cha devolve", "El ayuntamiento te dirá cómo te lo devuelve");

  public static final PaymentNotes DEFAULTS = new PaymentNotes(PaymentNotesScope.ALL, null, null);

  public PaymentNotes {
    Objects.requireNonNull(scope, "scope");
  }

  /** Textos para todas as instalacións ({@link PaymentNotesScope#ALL}). */
  public PaymentNotes(@Nullable LocalizedText payment, @Nullable LocalizedText refund) {
    this(PaymentNotesScope.ALL, payment, refund);
  }

  /** Cada instalación leva os seus textos. */
  public boolean byFacility() {
    return scope == PaymentNotesScope.FACILITY;
  }

  /** A nota de pago que se mostra: a do concello ou a de Furar. */
  public LocalizedText effectivePayment() {
    return payment == null ? DEFAULT_PAYMENT : payment;
  }

  public LocalizedText effectiveRefund() {
    return refund == null ? DEFAULT_REFUND : refund;
  }

  /**
   * Comproba e normaliza: os dous textos dunha nota baleiros = o texto por defecto; un só, erro por
   * campo ({@code paymentNotes.payment.gl}…).
   */
  public static PaymentNotes of(
      @Nullable String paymentGl,
      @Nullable String paymentEs,
      @Nullable String refundGl,
      @Nullable String refundEs) {
    return of(PaymentNotesScope.ALL, paymentGl, paymentEs, refundGl, refundEs);
  }

  /** {@link #of(String, String, String, String)} co ámbito. */
  public static PaymentNotes of(
      PaymentNotesScope scope,
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
    return new PaymentNotes(scope, payment, refund);
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
