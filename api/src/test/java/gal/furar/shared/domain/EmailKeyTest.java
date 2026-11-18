// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.Holder;
import gal.furar.staff.domain.EmailAddress;
import org.junit.jupiter.api.Test;

/**
 * Unha soa normalización do correo (ADR-0031, punto 4): a mesma clave no persoal, nas contas
 * coñecidas polo correo e nas reservas.
 */
class EmailKeyTest {

  @Test
  void stripsAndLowercasesWithoutLocaleSurprises() {
    assertThat(EmailKey.of("  Ana.REY@Example.ORG \t")).isEqualTo("ana.rey@example.org");
    // Locale.ROOT: o «I» non pasa a «ı» nun servidor en turco.
    assertThat(EmailKey.of("INES@EXAMPLE.ORG")).isEqualTo("ines@example.org");
  }

  @Test
  void staffAndBookingsUseTheSameKey() {
    String typed = " Ana.Rey@Example.org ";

    assertThat(EmailAddress.of(typed).value()).isEqualTo(EmailKey.of(typed));
    assertThat(Holder.walkIn("Ana Rey", typed.strip(), null, Language.GL).emailKey())
        .isEqualTo(EmailKey.of(typed));
    assertThat(Holder.walkIn("Ana Rey", null, null, Language.GL).emailKey()).isNull();
  }
}
