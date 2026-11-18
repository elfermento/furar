// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.domain.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * NIF da entidade responsable: letra de entidade, 7 díxitos e control (díxito ou letra). A táboa é
 * a mesma ca a de {@code entity-nif.spec.ts} da web; o baleiro quere dicir válido.
 */
class EntityNifTest {

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(
      nullValues = "-",
      value = {
        // Concello (P), patronato (Q), fundación (G, control en díxito e en letra), empresa (B)
        "P1599900F, -",
        "Q1573421C, -",
        "G70123450, -",
        "G7012345J, -",
        "B70345673, -",
        "q1573421c, -",
        "'Q-1573421 C', -",
        "P1505400J, -",
        "P1503700E, -",
        "P3208600A, -",
        "Q1573421D, municipality.nif-check-digit",
        "P1503700J, municipality.nif-check-digit",
        "34871265E, municipality.nif-personal",
        "X1234567L, municipality.nif-personal",
        "K1234567A, municipality.nif-personal",
        "I1234567A, validation.pattern",
        "P150540J, validation.pattern",
        "P15054000J, validation.pattern",
        "P1505400K, validation.pattern",
      })
  void checksTheExamples(String raw, String expected) {
    String nif = EntityNif.normalise(raw);
    assertThat(nif).isNotNull();
    Optional<String> code = EntityNif.check(nif).map(ErrorCode::code);
    assertThat(code).isEqualTo(Optional.ofNullable(expected));
    assertThat(EntityNif.isValid(raw)).isEqualTo(expected == null);
  }

  @Test
  void normalisesCaseSpacesDotsAndHyphens() {
    assertThat(EntityNif.normalise(" q.1573421-c ")).isEqualTo("Q1573421C");
    assertThat(EntityNif.normalise(" - ")).isNull();
    assertThat(EntityNif.normalise(null)).isNull();
    assertThat(EntityNif.isValid(null)).isFalse();
  }
}
