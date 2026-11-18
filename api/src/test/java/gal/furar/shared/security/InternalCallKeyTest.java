// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * ADR-0029 e ADR-0043: as claves coas que Keycloak chama á API saen do segredo compartido, unha por
 * propósito. Os vectores son os mesmos ca os de {@code SecondFactorPolicyTest} e {@code
 * AccountClosureApiTest} da extensión de Keycloak (e os de {@code openssl dgst -sha256 -hmac}): se
 * un lado cambia, os dous tests fallan.
 */
class InternalCallKeyTest {

  static final String SECRET = "furar-demo-invitation-proof-secret-not-for-production";

  @Test
  void isTheHmacOfThePurposeWithTheSharedSecret() {
    InternalCallKey key =
        InternalCallKey.derive("  " + SECRET + "\n", InternalCallKey.SECOND_FACTOR);
    assertThat(key.value()).isEqualTo("SO5VzVez0ZcaWEMnksh55SyqsPb6Q-3iXX64hd2TkoM");
    assertThat(key.matches("SO5VzVez0ZcaWEMnksh55SyqsPb6Q-3iXX64hd2TkoM")).isTrue();
    assertThat(key.matches(SECRET)).isFalse();
    assertThat(key.matches(null)).isFalse();
    assertThat(key.toString()).doesNotContain(key.value());
  }

  @Test
  void eachPurposeHasItsOwnKey() {
    InternalCallKey closure = InternalCallKey.derive(SECRET, InternalCallKey.ACCOUNT_CLOSURE);
    assertThat(closure.value()).isEqualTo("tlSMz70rcawpGl90zbi0aY6ELJQds3cBfBg3Ge42Lpo");
    assertThat(
            closure.matches(InternalCallKey.derive(SECRET, InternalCallKey.SECOND_FACTOR).value()))
        .isFalse();
  }

  @Test
  void needsAUsableSecret() {
    assertThatThrownBy(() -> InternalCallKey.derive("curto", InternalCallKey.SECOND_FACTOR))
        .isInstanceOf(IllegalStateException.class);
  }
}
