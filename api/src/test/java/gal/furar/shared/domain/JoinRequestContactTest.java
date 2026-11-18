// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * «¿Es un concello?» (ADR-0042): sen correo non existe; un valor que non é un correo non arrinca.
 */
class JoinRequestContactTest {

  @Test
  void withoutAValueTheFeatureDoesNotExist() {
    assertThat(JoinRequestContact.parse(null).enabled()).isFalse();
    assertThat(JoinRequestContact.parse("").enabled()).isFalse();
    assertThat(JoinRequestContact.parse("   ").email()).isEmpty();
  }

  @Test
  void theEmailIsKeptWithoutSurroundingSpaces() {
    JoinRequestContact contact = JoinRequestContact.parse(" altas@example.org ");
    assertThat(contact.enabled()).isTrue();
    assertThat(contact.email()).contains("altas@example.org");
  }

  @Test
  void aValueThatIsNotAnEmailStopsTheStart() {
    for (String bad :
        new String[] {
          "altas", "altas@example", "Altas <altas@example.org>", "a@example.org,b@x.org"
        }) {
      assertThatThrownBy(() -> JoinRequestContact.parse(bad))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FURAR_JOIN_REQUEST_EMAIL");
    }
  }
}
