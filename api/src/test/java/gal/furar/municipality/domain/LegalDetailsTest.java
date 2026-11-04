// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
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
        privacy,
        accessibility,
        legalNotice);
  }

  @Test
  void blankValuesAreAbsentAndPagesDefaultToFurar() {
    LegalDetails l =
        new LegalDetails(
            " ",
            "",
            null,
            null,
            " ",
            "",
            null,
            "  dpd@naron.gal ",
            " ",
            " ",
            null,
            null,
            "",
            null,
            null,
            null);
    assertThat(l.ineCode()).isNull();
    assertThat(l.dir3Code()).isNull();
    assertThat(l.nif()).isNull();
    assertThat(l.dpoEmail()).isEqualTo("dpd@naron.gal");
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
            "15054",
            "L01150548",
            "Concello de Narón",
            "https://sede.naron.gal",
            "P1505400J",
            "Praza de Galicia, 1. 15570 Narón",
            "concello@naron.gal",
            "dpd@naron.gal",
            "+34 981 000 000",
            " Aloxamentos Exemplo SL ",
            "accesibilidade@naron.gal",
            "https://sede.naron.gal/reclamacions",
            "Departamento de Novas Tecnoloxías",
            new LegalPageSettings(LegalPageMode.URL, "https://naron.gal/privacidade"),
            new LegalPageSettings(LegalPageMode.TEXT, null),
            LegalPageSettings.FURAR);
    assertThat(l.processor()).isEqualTo("Aloxamentos Exemplo SL");
    assertThat(l.accessibilityComplaintUrl()).isEqualTo("https://sede.naron.gal/reclamacions");
    assertThat(l.privacy().externalUrl()).isEqualTo("https://naron.gal/privacidade");
    assertThat(l.accessibility().externalUrl()).isNull();
  }

  @Test
  void theOwnPageAddressSurvivesAnotherMode() {
    LegalDetails l =
        withPages(
            new LegalPageSettings(LegalPageMode.FURAR, "https://naron.gal/privacidade"),
            LegalPageSettings.FURAR,
            LegalPageSettings.FURAR);
    assertThat(l.privacy().url()).isEqualTo("https://naron.gal/privacidade");
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
                    new LegalPageSettings(LegalPageMode.URL, "http://naron.gal/aviso")));
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
                    "1505",
                    "naron",
                    "x".repeat(201),
                    "http://sede.naron.gal",
                    "1505400J",
                    "x".repeat(201),
                    "concello",
                    "non-é-correo",
                    "981-000-000",
                    "x".repeat(201),
                    "non-é-correo",
                    "https://ok.gal/r",
                    null,
                    new LegalPageSettings(LegalPageMode.URL, "javascript:alert(1)"),
                    null,
                    null));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly(
            "legal.ineCode",
            "legal.dir3Code",
            "legal.legalEntityName",
            "legal.eOfficeUrl",
            "legal.nif",
            "legal.address",
            "legal.generalEmail",
            "legal.dpoEmail",
            "legal.dpoPhone",
            "legal.processor",
            "legal.privacyUrl",
            "legal.accessibilityEmail");
  }

  @Test
  void withoutPageNothingIsPublishedAndTheAddressIsKept() {
    LegalPageSettings none = new LegalPageSettings(LegalPageMode.NONE, "https://naron.gal/p");
    assertThat(none.published()).isFalse();
    assertThat(none.externalUrl()).isNull();
    assertThat(none.url()).isEqualTo("https://naron.gal/p");
    assertThat(LegalPageSettings.FURAR.published()).isTrue();
    assertThat(LegalPageMode.fromName("NONE")).contains(LegalPageMode.NONE);
  }
}
