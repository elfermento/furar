// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Enderezo: obrigatorios, formatos e normalización. */
class AddressTest {

  static final BigDecimal LAT = new BigDecimal("43.516712");
  static final BigDecimal LON = new BigDecimal("-8.183301");

  @Test
  void normalisesSpacesNumberAndScale() {
    Address a =
        new Address(" Rúa do Alto ", "  ", "15570", " Narón", "15054", new BigDecimal("43.5"), LON);
    assertThat(a.street()).isEqualTo("Rúa do Alto");
    assertThat(a.streetNumber()).isNull();
    assertThat(a.locality()).isEqualTo("Narón");
    assertThat(a.latitude()).isEqualByComparingTo("43.5").hasScaleOf(6);
  }

  @Test
  void missingPartsAreRequiredFieldByField() {
    assertThatThrownBy(() -> new Address(null, "1", " ", "Narón", "15054", null, LON))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(FacilityError.ADDRESS_REQUIRED);
              assertThat(e.violations())
                  .extracting(FieldViolation::field)
                  .containsExactly("address.street", "address.postalCode", "address.latitude");
              assertThat(e.violations())
                  .extracting(FieldViolation::code)
                  .containsOnly("validation.required");
            });
  }

  @Test
  void wrongFormatsAreInvalid() {
    assertThatThrownBy(
            () ->
                new Address(
                    "Rúa",
                    null,
                    "1557",
                    "Narón",
                    "ABCDE",
                    new BigDecimal("90.1"),
                    new BigDecimal("-180.5")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(FacilityError.ADDRESS_INVALID);
              assertThat(e.violations())
                  .extracting(FieldViolation::field)
                  .containsExactly(
                      "address.postalCode",
                      "address.ineCode",
                      "address.latitude",
                      "address.longitude");
            });
  }

  @Test
  void longTextsAreInvalid() {
    assertThatThrownBy(
            () ->
                new Address(
                    "x".repeat(Address.STREET_MAX + 1), null, "15570", "Narón", "15054", LAT, LON))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FacilityError.ADDRESS_INVALID));
  }

  @Test
  void thePointIsOptionalButGoesWhole() {
    Address a = new Address("Rúa do Alto", null, "15570", "Narón", "15054", null, null);
    assertThat(a.hasPoint()).isFalse();
    assertThat(a.latitude()).isNull();
    assertThatThrownBy(() -> new Address("Rúa", null, "15570", "Narón", "15054", LAT, null))
        .isInstanceOfSatisfying(
            DomainException.class,
            e ->
                assertThat(e.violations())
                    .extracting(FieldViolation::field)
                    .containsExactly("address.longitude"));
  }
}
