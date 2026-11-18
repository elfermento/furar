// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Datos legais (ADR-0017 punto 8): opcionais, normalizados e validados por campo. Cada páxina leva
 * o seu modo; a páxina propia pide enderezo só no modo URL, e cambiar de modo non o borra.
 */
class LegalDetailsTest {

  private static LegalDetails withPages(
      LegalPageSettings privacy, LegalPageSettings accessibility, LegalPageSettings legalNotice) {
    return new LegalDetails(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        privacy,
        accessibility,
        legalNotice);
  }

  @Test
  void blankValuesAreAbsentAndPagesDefaultToFurar() {
    LegalDetails l =
        new LegalDetails(
            "",
            null,
            null,
            " ",
            "",
            null,
            "  dpd@naron.example.org ",
            " ",
            " ",
            null,
            null,
            "",
            " ",
            "",
            null,
            null,
            null,
            null);
    assertThat(l.dir3Code()).isNull();
    assertThat(l.nif()).isNull();
    assertThat(l.dpoEmail()).isEqualTo("dpd@naron.example.org");
    assertThat(l.dpoPhone()).isNull();
    assertThat(l.processor()).isNull();
    assertThat(l.accessibilityUnit()).isNull();
    assertThat(l.privacy()).isEqualTo(LegalPageSettings.FURAR);
    assertThat(l.legalNotice().mode()).isEqualTo(LegalPageMode.FURAR);
  }

  @Test
  void validDetailsAreKept() {
    LegalDetails l =
        new LegalDetails(
            "L01150548",
            "Concello de Narón",
            "https://sede.naron.example.org",
            "P1505400J",
            "Praza de Galicia, 1. 15570 Narón",
            "concello@naron.example.org",
            "dpd@naron.example.org",
            "+34 981 000 000",
            " Aloxamentos Exemplo SL ",
            "accesibilidade@naron.example.org",
            "https://sede.naron.example.org/reclamacions",
            "Departamento de Novas Tecnoloxías",
            " 981 000 000 ",
            "https://sede.naron.example.org/rat",
            "https://naron.example.org/normativa-instalacions",
            new LegalPageSettings(LegalPageMode.URL, "https://naron.example.org/privacidade"),
            new LegalPageSettings(LegalPageMode.TEXT, null),
            LegalPageSettings.FURAR);
    assertThat(l.processor()).isEqualTo("Aloxamentos Exemplo SL");
    assertThat(l.accessibilityComplaintUrl())
        .isEqualTo("https://sede.naron.example.org/reclamacions");
    assertThat(l.privacy().externalUrl()).isEqualTo("https://naron.example.org/privacidade");
    assertThat(l.accessibility().externalUrl()).isNull();
    assertThat(l.generalPhone()).isEqualTo("981 000 000");
    assertThat(l.processingRegisterUrl()).isEqualTo("https://sede.naron.example.org/rat");
    assertThat(l.useRegulationUrl()).isEqualTo("https://naron.example.org/normativa-instalacions");
  }

  private static LegalDetails withNif(String nif) {
    return new LegalDetails(
        null, null, null, nif, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null);
  }

  @Test
  void theNifIsNormalisedSoLowercaseImportsWork() {
    assertThat(withNif(" q-1573421 c ").nif()).isEqualTo("Q1573421C");
    assertThat(withNif(" . ").nif()).isNull();
  }

  @Test
  void aPersonalOrMisspelledNifHasItsOwnCode() {
    for (String[] c :
        new String[][] {
          {"34871265E", "municipality.nif-personal"},
          {"Q1573421D", "municipality.nif-check-digit"},
          {"T1573421C", "validation.pattern"}
        }) {
      DomainException e = catchThrowableOfType(DomainException.class, () -> withNif(c[0]));
      assertThat(e.violations())
          .extracting(FieldViolation::field, FieldViolation::code)
          .as(c[0])
          .containsExactly(tuple("legal.nif", c[1]));
    }
  }

  @Test
  void theOwnPageAddressSurvivesAnotherMode() {
    LegalDetails l =
        withPages(
            new LegalPageSettings(LegalPageMode.FURAR, "https://naron.example.org/privacidade"),
            LegalPageSettings.FURAR,
            LegalPageSettings.FURAR);
    assertThat(l.privacy().url()).isEqualTo("https://naron.example.org/privacidade");
    assertThat(l.privacy().externalUrl()).as("só leva a ela no modo URL").isNull();
  }

  @Test
  void theOwnPageModeNeedsAnAddress() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () ->
                withPages(
                    LegalPageSettings.FURAR,
                    new LegalPageSettings(LegalPageMode.URL, " "),
                    new LegalPageSettings(LegalPageMode.URL, "http://naron.example.org/aviso")));
    assertThat(e.violations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(
            tuple("legal.accessibilityUrl", "validation.required"),
            tuple("legal.legalNoticeUrl", "validation.pattern"));
  }

  @Test
  void everyInvalidFieldIsReported() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () ->
                new LegalDetails(
                    "naron",
                    "x".repeat(201),
                    "http://sede.naron.example.org",
                    "1505400J",
                    "x".repeat(201),
                    "concello",
                    "non-é-correo",
                    "981-000-000",
                    "x".repeat(201),
                    "non-é-correo",
                    "https://ok.gal/r",
                    null,
                    "981-000",
                    "http://sede.naron.example.org/rat",
                    "https://" + "x".repeat(500),
                    new LegalPageSettings(LegalPageMode.URL, "javascript:alert(1)"),
                    null,
                    null));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly(
            "legal.dir3Code",
            "legal.legalEntityName",
            "legal.eOfficeUrl",
            "legal.nif",
            "legal.address",
            "legal.generalEmail",
            "legal.dpoEmail",
            "legal.dpoPhone",
            "legal.generalPhone",
            "legal.processingRegisterUrl",
            "legal.processor",
            "legal.privacyUrl",
            "legal.accessibilityEmail",
            "legal.useRegulationUrl");
  }

  @Test
  void withoutPageNothingIsPublishedAndTheAddressIsKept() {
    LegalPageSettings none =
        new LegalPageSettings(LegalPageMode.NONE, "https://naron.example.org/p");
    assertThat(none.published()).isFalse();
    assertThat(none.externalUrl()).isNull();
    assertThat(none.url()).isEqualTo("https://naron.example.org/p");
    assertThat(LegalPageSettings.FURAR.published()).isTrue();
    assertThat(LegalPageMode.fromName("NONE")).contains(LegalPageMode.NONE);
  }

  @Test
  void itIsReadyToOpenOnlyWithTheFiveDetailsTheDefaultLegalTextsUse() {
    assertThat(LegalDetails.EMPTY.readyToOpen()).isFalse();
    assertThat(
            opening(
                    "Concello de Narón",
                    "P1505400J",
                    "Praza de Galicia, 1",
                    "a@b.example.org",
                    "d@b.example.org")
                .readyToOpen())
        .isTrue();
    assertThat(
            opening(" ", "P1505400J", "Praza de Galicia, 1", "a@b.example.org", "d@b.example.org")
                .readyToOpen())
        .isFalse();
    assertThat(
            opening(
                    "Concello de Narón",
                    null,
                    "Praza de Galicia, 1",
                    "a@b.example.org",
                    "d@b.example.org")
                .readyToOpen())
        .isFalse();
    assertThat(
            opening("Concello de Narón", "P1505400J", "", "a@b.example.org", "d@b.example.org")
                .readyToOpen())
        .isFalse();
    assertThat(
            opening(
                    "Concello de Narón",
                    "P1505400J",
                    "Praza de Galicia, 1",
                    null,
                    "d@b.example.org")
                .readyToOpen())
        .isFalse();
    assertThat(
            opening(
                    "Concello de Narón",
                    "P1505400J",
                    "Praza de Galicia, 1",
                    "a@b.example.org",
                    null)
                .readyToOpen())
        .isFalse();
  }

  private static LegalDetails opening(
      @Nullable String entity,
      @Nullable String nif,
      @Nullable String address,
      @Nullable String generalEmail,
      @Nullable String dpoEmail) {
    return new LegalDetails(
        null,
        entity,
        null,
        nif,
        address,
        generalEmail,
        dpoEmail,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        LegalPageSettings.FURAR,
        LegalPageSettings.FURAR,
        LegalPageSettings.FURAR);
  }
}
