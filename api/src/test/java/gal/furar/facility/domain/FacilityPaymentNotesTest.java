// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import org.junit.jupiter.api.Test;

/** Pago e devolución de cada instalación (D2-11, «Distinto por instalación»). */
class FacilityPaymentNotesTest {

  @Test
  void blankNotesAreNoNotes() {
    assertThat(FacilityPaymentNotes.of(" ", null, "", null)).isEqualTo(FacilityPaymentNotes.NONE);
  }

  @Test
  void eachNoteGoesInBothLanguages() {
    FacilityPaymentNotes notes =
        FacilityPaymentNotes.of(" Na billeteira ", "En la taquilla", null, null);
    assertThat(notes.payment()).isNotNull();
    assertThat(notes.payment().gl()).isEqualTo("Na billeteira");
    assertThat(notes.refund()).isNull();

    DomainException e =
        catchThrowableOfType(
            DomainException.class, () -> FacilityPaymentNotes.of(null, null, null, "Solo"));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly("paymentNotes.refund.gl");
  }

  @Test
  void tooLongIsASizeError() {
    String long501 = "x".repeat(FacilityPaymentNotes.MAX + 1);
    DomainException e =
        catchThrowableOfType(
            DomainException.class, () -> FacilityPaymentNotes.of(long501, "ok", null, null));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly("paymentNotes.payment.gl");
  }
}
