// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A cabeceira {@code Idempotency-Key} (ADR-0025, punto 19): falta, mal formada e ben. */
class IdempotencyKeyTest {

  @Test
  void aUuidIsAKeyAsItComes() {
    String uuid = "8e03978e-40d5-43e8-bc93-6894a57f9324";
    assertThat(IdempotencyKey.parse(uuid).value()).isEqualTo(uuid);
    assertThat(IdempotencyKey.parse("  " + uuid + " ").value()).isEqualTo(uuid);
  }

  @Test
  void theStructuredFieldStringOfTheDraftLosesItsQuotes() {
    assertThat(IdempotencyKey.parse("\"abc-123\"").value()).isEqualTo("abc-123");
  }

  @Test
  void upTo255VisibleAsciiCharacters() {
    assertThat(IdempotencyKey.parse("a".repeat(255)).value()).hasSize(255);
    assertThat(IdempotencyKey.parse("!#$%&'()*+,-./:;<=>?@[]^_`{|}~").value()).hasSize(30);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void withoutAKeyItIsMissing(String header) {
    assertThatThrownBy(() -> IdempotencyKey.parse(header))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.IDEMPOTENCY_KEY_MISSING));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a b", "\"\"", "a\"b", "a\\b", "clave-ñ", "tab\there", "\"abc"})
  void anythingElseIsInvalid(String header) {
    assertThatThrownBy(() -> IdempotencyKey.parse(header))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(BookingError.IDEMPOTENCY_KEY_INVALID);
              assertThat(e.errorCode().status()).isEqualTo(400);
            });
  }

  @Test
  void tooLongIsInvalid() {
    assertThatThrownBy(() -> IdempotencyKey.parse("a".repeat(256)))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.IDEMPOTENCY_KEY_INVALID));
  }

  @Test
  void theCodesAreStable() {
    assertThat(BookingError.IDEMPOTENCY_KEY_MISSING.code())
        .isEqualTo("booking.idempotency-key-missing");
    assertThat(BookingError.IDEMPOTENCY_KEY_INVALID.code())
        .isEqualTo("booking.idempotency-key-invalid");
    assertThat(BookingError.IDEMPOTENCY_KEY_REUSED.code())
        .isEqualTo("booking.idempotency-key-reused");
    assertThat(BookingError.IDEMPOTENCY_KEY_REUSED.status()).isEqualTo(422);
  }
}
