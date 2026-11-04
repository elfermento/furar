// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class MunicipalityTest {

  static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  @Test
  void slugRules() {
    assertThat(new Slug("a-coruna").value()).isEqualTo("a-coruna");
    assertThat(new Slug("o-porrino-2").value()).isEqualTo("o-porrino-2");
    for (String bad :
        List.of("A", "a", "-naron", "naron-", "na--ron", "Narón", "na ron", "x".repeat(41))) {
      assertError(() -> new Slug(bad), MunicipalityError.SLUG_INVALID);
    }
    assertError(() -> new Slug(null), MunicipalityError.SLUG_INVALID);
    for (String reserved : Slug.RESERVED) {
      assertError(() -> new Slug(reserved), MunicipalityError.SLUG_RESERVED);
    }
  }

  @Test
  void contrastFollowsWcag() {
    HexColor black = new HexColor("#000000");
    HexColor white = new HexColor("#FFFFFF");
    assertThat(white.value()).isEqualTo("#ffffff");
    assertThat(black.contrastWith(white))
        .isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.01));
    assertThat(new HexColor("#777777").contrastWith(white))
        .isCloseTo(4.48, org.assertj.core.data.Offset.offset(0.01));
    assertError(() -> new HexColor("#fff"), MunicipalityError.THEME_COLOR_INVALID);
    assertError(() -> new HexColor(null), MunicipalityError.THEME_COLOR_INVALID);
  }

  @Test
  void themeNeedsAaContrast() {
    assertThat(Theme.DEFAULT.primary().value()).isEqualTo("#0b4f8a");
    assertError(
        () -> theme("#0b4f8a", "#ffffff", "#b3541e", "#ffffff", "#777777"),
        MunicipalityError.THEME_LOW_CONTRAST);
    assertError(
        () -> theme("#0b4f8a", "#1d4f8a", "#b3541e", "#ffffff", "#1a1a1a"),
        MunicipalityError.THEME_LOW_CONTRAST);
    assertError(
        () -> theme("#eeeeee", "#000000", "#b3541e", "#ffffff", "#1a1a1a"),
        MunicipalityError.THEME_LOW_CONTRAST);
    assertError(
        () -> theme("#0b4f8a", "#ffffff", "#f0f0f0", "#ffffff", "#1a1a1a"),
        MunicipalityError.THEME_LOW_CONTRAST);
    try {
      theme("#0b4f8a", "#ffffff", "#f0f0f0", "#ffffff", "#1a1a1a");
    } catch (DomainException e) {
      assertThat(e.params()).containsEntry("pair", "accent/background").containsKey("ratio");
    }
  }

  @Test
  void createAndUpdate() {
    Municipality m = create("Narón", List.of(Language.GL, Language.ES), Language.GL);
    assertThat(m.version()).isNull();
    assertThat(m.createdAt()).isEqualTo(NOW);

    Municipality updated =
        m.update(
            " Narón (A Coruña) ",
            List.of(Language.ES),
            Language.ES,
            Theme.DEFAULT,
            LegalDetails.EMPTY,
            NOW.plusSeconds(60));
    assertThat(updated.name()).isEqualTo("Narón (A Coruña)");
    assertThat(updated.slug()).isEqualTo(m.slug());
    assertThat(updated.id()).isEqualTo(m.id());
    assertThat(updated.languages()).containsExactly(Language.ES);
    assertThat(updated.defaultLanguage()).isEqualTo(Language.ES);
    assertThat(updated.createdAt()).isEqualTo(NOW);
    assertThat(updated.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(updated.theme()).isEqualTo(Theme.DEFAULT);

    Municipality stored =
        Municipality.rehydrate(
            m.id(),
            m.slug(),
            "Narón",
            List.of(Language.GL),
            Language.GL,
            Theme.DEFAULT,
            LegalDetails.EMPTY,
            4,
            NOW,
            NOW);
    assertThat(stored.version()).isEqualTo(4);
  }

  @Test
  void invariants() {
    assertError(
        () -> create(" ", List.of(Language.GL), Language.GL), MunicipalityError.NAME_INVALID);
    assertError(
        () -> create("x".repeat(101), List.of(Language.GL), Language.GL),
        MunicipalityError.NAME_INVALID);
    assertError(() -> create("N", List.of(), Language.GL), MunicipalityError.LANGUAGES_INVALID);
    assertError(
        () -> create("N", List.of(Language.GL), Language.ES), MunicipalityError.LANGUAGES_INVALID);
    assertError(
        () -> create("N", List.of(Language.GL, Language.GL), Language.GL),
        MunicipalityError.LANGUAGES_INVALID);
    assertError(() -> create("N", null, Language.GL), MunicipalityError.LANGUAGES_INVALID);
  }

  static Municipality create(String name, List<Language> languages, Language defaultLanguage) {
    return Municipality.create(
        MunicipalityId.newId(),
        new Slug("naron"),
        name,
        languages,
        defaultLanguage,
        Theme.DEFAULT,
        NOW);
  }

  static Theme theme(String p, String op, String a, String b, String t) {
    return new Theme(
        new HexColor(p), new HexColor(op), new HexColor(a), new HexColor(b), new HexColor(t));
  }

  static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }
}
