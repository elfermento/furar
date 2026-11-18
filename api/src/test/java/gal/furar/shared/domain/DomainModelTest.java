// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainModelTest {

  @Test
  void localizedTextNeedsBothLanguages() {
    assertThat(new LocalizedText(" Ola ", "Hola").gl()).isEqualTo("Ola");
    assertThat(new LocalizedText("Ola", "Hola").in(Language.ES)).isEqualTo("Hola");
    assertThat(new LocalizedText("Ola", "Hola").in(Language.GL)).isEqualTo("Ola");
    assertThatThrownBy(() -> new LocalizedText("Ola", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LocalizedText(null, "Hola"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LocalizedText("x".repeat(501), "Hola"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void languageCodes() {
    assertThat(Language.fromCode("gl")).contains(Language.GL);
    assertThat(Language.fromCode("es")).contains(Language.ES);
    assertThat(Language.fromCode("en")).isEmpty();
    assertThat(Language.ES.code()).isEqualTo("es");
  }

  @Test
  void domainExceptionCarriesCodeParamsAndViolations() {
    DomainException e =
        DomainException.validation(List.of(FieldViolation.of("name", ValidationError.REQUIRED)));
    assertThat(e.errorCode()).isEqualTo(CommonError.VALIDATION_FAILED);
    assertThat(e.getMessage()).isEqualTo("validation.failed");
    assertThat(e.violations())
        .extracting(FieldViolation::code)
        .containsExactly("validation.required");
    assertThat(new DomainException(CommonError.INTERNAL, null).params()).isEmpty();
    assertThat(new FieldViolation("f", "c", null).params()).isEmpty();
    assertThat(FieldViolation.of("f", CommonError.INTERNAL, Map.of("a", 1)).params())
        .containsEntry("a", 1);
  }

  @Test
  void pageResultMaps() {
    PageResult<Integer> page = new PageResult<>(List.of(1, 2), 0, 2, 5);
    assertThat(page.map(i -> i * 10).items()).containsExactly(10, 20);
    assertThat(page.map(i -> i).totalItems()).isEqualTo(5);
  }
}
