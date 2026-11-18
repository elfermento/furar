// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupportContactTest {

  @Test
  void withoutAValueThereIsNoSupportEmail() {
    assertThat(SupportContact.parse(null).email()).isEmpty();
    assertThat(SupportContact.parse("").email()).isEmpty();
    assertThat(SupportContact.parse("   ").email()).isEmpty();
  }

  @Test
  void theEmailIsKeptAsWrittenWithoutSurroundingSpaces() {
    assertThat(SupportContact.parse(" soporte@example.org ").email())
        .contains("soporte@example.org");
    assertThat(SupportContact.parse("Soporte.Reservas+web@sub-dominio.example.org").email())
        .contains("Soporte.Reservas+web@sub-dominio.example.org");
  }

  @Test
  void aValueThatIsNotAnEmailStopsTheStart() {
    for (String bad :
        new String[] {
          "soporte",
          "@example.org",
          "soporte@",
          "soporte@example",
          "soporte @example.org",
          "soporte@example.org?subject=x",
          "soporte@example.org,outra@example.org",
          "Soporte <soporte@example.org>",
          "mailto:soporte@example.org",
          "soporte@-example.org",
          "soporte@example..org",
          "a".repeat(SupportContact.MAX_LENGTH) + "@example.org"
        }) {
      assertThatThrownBy(() -> SupportContact.parse(bad))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FURAR_SUPPORT_EMAIL");
    }
    assertThatThrownBy(() -> new SupportContact(Optional.of("non é un correo")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
