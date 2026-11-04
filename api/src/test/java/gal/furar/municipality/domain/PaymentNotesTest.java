// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import org.junit.jupiter.api.Test;

/**
 * Pago e devolución (D2-11): o ámbito garda os textos do concello aínda que cada instalación leve
 * os seus, e sen texto vale o de Furar. Revisión do 2026-11-03.
 */
class PaymentNotesTest {

  @Test
  void byDefaultTheTextsAreTheSameForEveryFacilityAndFurarOnes() {
    assertThat(PaymentNotes.DEFAULTS.scope()).isEqualTo(PaymentNotesScope.ALL);
    assertThat(PaymentNotes.DEFAULTS.byFacility()).isFalse();
    assertThat(PaymentNotes.DEFAULTS.effectivePayment()).isEqualTo(PaymentNotes.DEFAULT_PAYMENT);
    assertThat(PaymentNotes.DEFAULTS.effectiveRefund()).isEqualTo(PaymentNotes.DEFAULT_REFUND);
  }

  @Test
  void byFacilityKeepsTheMunicipalityTexts() {
    PaymentNotes notes =
        PaymentNotes.of(PaymentNotesScope.FACILITY, "Con tarxeta", "Con tarjeta", null, null);
    assertThat(notes.byFacility()).isTrue();
    assertThat(notes.payment()).isNotNull();
    assertThat(notes.payment().gl()).isEqualTo("Con tarxeta");
    assertThat(notes.refund()).isNull();
  }

  @Test
  void aNoteInOnlyOneLanguageIsAFieldError() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () -> PaymentNotes.of(PaymentNotesScope.ALL, "Só galego", null, null, null));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly("paymentNotes.payment.es");
  }

  @Test
  void scopeNamesAreStrict() {
    assertThat(PaymentNotesScope.fromName("FACILITY")).contains(PaymentNotesScope.FACILITY);
    assertThat(PaymentNotesScope.fromName("facility")).isEmpty();
  }
}
