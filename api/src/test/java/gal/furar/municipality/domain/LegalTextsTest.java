// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.Language;
import org.junit.jupiter.api.Test;

/** Textos propios das páxinas legais: un idioma pode faltar, ningún = sen texto; tamaño máximo. */
class LegalTextsTest {

  @Test
  void aTextWithoutLanguagesIsNoText() {
    LegalTexts t =
        new LegalTexts(new LegalText(" ", null), new LegalText("<p>Ola</p>", null), null);
    assertThat(t.privacy()).isNull();
    assertThat(t.accessibility().in(Language.GL)).isEqualTo("<p>Ola</p>");
    assertThat(t.accessibility().in(Language.ES)).isNull();
  }

  @Test
  void eachLanguageHasAMaximumSize() {
    String big = "<p>" + "x".repeat(LegalText.MAX) + "</p>";
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () -> new LegalTexts(null, null, new LegalText("<p>ok</p>", big)));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly("legalTexts.legalNotice.es");
  }
}
