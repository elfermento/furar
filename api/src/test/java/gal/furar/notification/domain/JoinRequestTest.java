// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import org.junit.jupiter.api.Test;

/** Os campos dunha solicitude de alta (ADR-0042). */
class JoinRequestTest {

  @Test
  void theFieldsAreKeptWithoutSurroundingSpacesAndTheOptionalOnesCanBeEmpty() {
    JoinRequest request =
        new JoinRequest(
            " Concello de Exemplo ", " Ana Rey ", "  ", " ana@example.org ", " 981 000 000 ", "");
    assertThat(request.municipality()).isEqualTo("Concello de Exemplo");
    assertThat(request.name()).isEqualTo("Ana Rey");
    assertThat(request.role()).isNull();
    assertThat(request.email()).isEqualTo("ana@example.org");
    assertThat(request.phone()).isEqualTo("981 000 000");
    assertThat(request.message()).isNull();
  }

  @Test
  void everyRequiredFieldIsCheckedAtOnce() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class, () -> new JoinRequest(" ", null, null, "", null, null));
    assertThat(e.errorCode().code()).isEqualTo("validation.failed");
    assertThat(e.violations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("municipality", "validation.required"),
            org.assertj.core.groups.Tuple.tuple("name", "validation.required"),
            org.assertj.core.groups.Tuple.tuple("email", "validation.required"),
            org.assertj.core.groups.Tuple.tuple("phone", "validation.required"));
  }

  @Test
  void anIncompleteEmailOrAPhoneWithLettersIsAPatternError() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () -> new JoinRequest("Concello", "Ana", null, "ana@example", "981 abc", null));
    assertThat(e.violations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("email", "validation.pattern"),
            org.assertj.core.groups.Tuple.tuple("phone", "validation.pattern"));
  }

  @Test
  void tooLongValuesAreSizeErrors() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () ->
                new JoinRequest(
                    "C".repeat(JoinRequest.MUNICIPALITY_MAX + 1),
                    "Ana",
                    "R".repeat(JoinRequest.ROLE_MAX + 1),
                    "ana@example.org",
                    "12",
                    "M".repeat(JoinRequest.MESSAGE_MAX + 1)));
    assertThat(e.violations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("municipality", "validation.size"),
            org.assertj.core.groups.Tuple.tuple("role", "validation.size"),
            org.assertj.core.groups.Tuple.tuple("phone", "validation.size"),
            org.assertj.core.groups.Tuple.tuple("message", "validation.size"));
  }

  @Test
  void theTextOfTheRecordOnlyCarriesTheMunicipality() {
    JoinRequest request =
        new JoinRequest(
            "Concello de Exemplo", "Ana Rey", null, "ana@example.org", "981000000", null);
    assertThat(request.toString())
        .contains("Concello de Exemplo")
        .doesNotContain("Ana")
        .doesNotContain("@")
        .doesNotContain("981");
  }
}
