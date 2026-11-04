// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.municipality.application.port.in.LegalData;
import gal.furar.municipality.application.port.in.LegalTextsData;
import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.LegalPageMode;
import gal.furar.municipality.domain.LegalTexts;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import org.junit.jupiter.api.Test;

/** Datos e textos legais tal como chegan → dominio (ADR-0017 punto 8). */
class MunicipalityInputTest {

  private static LegalData legal(
      String dpoEmail, String dpoPhone, String privacyMode, String dpoContact) {
    return new LegalData(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        dpoEmail,
        dpoPhone,
        null,
        null,
        null,
        null,
        privacyMode,
        "https://naron.gal/privacidade",
        null,
        null,
        "TEXT",
        null,
        dpoContact);
  }

  @Test
  void modesArriveByNameAndDefaultToFurar() {
    LegalDetails l = MunicipalityInput.legal(legal(null, null, "URL", null));
    assertThat(l.privacy().mode()).isEqualTo(LegalPageMode.URL);
    assertThat(l.accessibility().mode()).isEqualTo(LegalPageMode.FURAR);
    assertThat(l.legalNotice().mode()).isEqualTo(LegalPageMode.TEXT);
  }

  @Test
  void anUnknownModeIsAFieldError() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class, () -> MunicipalityInput.legal(legal(null, null, "PDF", null)));
    assertThat(e.violations())
        .extracting(FieldViolation::field)
        .containsExactly("legal.privacyMode");
  }

  @Test
  void anOldFileSplitsTheFreeTextDpoIntoEmailAndPhone() {
    LegalDetails l =
        MunicipalityInput.legal(legal(null, null, null, "DPD: dpd@naron.gal, tel. 981 000 000"));
    assertThat(l.dpoEmail()).isEqualTo("dpd@naron.gal");
    assertThat(l.dpoPhone()).isEqualTo("981 000 000");
  }

  @Test
  void theNewDpoFieldsWinOverTheOldText() {
    LegalDetails l =
        MunicipalityInput.legal(legal("novo@naron.gal", null, null, "vello@naron.gal"));
    assertThat(l.dpoEmail()).isEqualTo("novo@naron.gal");
    assertThat(l.dpoPhone()).isNull();
  }

  @Test
  void legalTextsGoThroughTheSanitizer() {
    LegalTexts t =
        MunicipalityInput.legalTexts(
            new LegalTextsData(new LegalTextsData.Text("<p>gl</p>", "  "), null, null),
            html -> html == null || html.isBlank() ? null : "saneado:" + html);
    assertThat(t.privacy().gl()).isEqualTo("saneado:<p>gl</p>");
    assertThat(t.privacy().es()).isNull();
    assertThat(t.accessibility()).isNull();
    assertThat(MunicipalityInput.legalTexts(null, html -> html)).isEqualTo(LegalTexts.EMPTY);
  }
}
