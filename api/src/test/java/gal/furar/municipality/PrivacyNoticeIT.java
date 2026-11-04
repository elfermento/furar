// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import org.junit.jupiter.api.Test;

/**
 * Datos do despregue para a páxina de privacidade (RGPD art. 13, ADR-0017, AP2-02): públicos, sen
 * concello no URL e só cos prazos do rexistro de actividade. O responsable, o DPD, os dereitos e o
 * encargado son datos legais do concello (revisión do 2026-11-03): ningún sae da configuración.
 */
class PrivacyNoticeIT extends AbstractIT {

  @Test
  void anyoneReadsTheDeploymentPrivacyNotice() {
    Api.Response notice = api().get("/api/v1/privacy-notice").send();
    assertThat(notice.status()).isEqualTo(200);
    // Os prazos do rexistro de actividade (ADR-0020, revisión do 2026-10-29).
    assertThat(notice.json().get("activityVisible").asString()).isEqualTo("P1Y");
    assertThat(notice.json().get("activityRetention").asString()).isEqualTo("P3Y");
    assertThat(notice.json().has("accountController")).isFalse();
    assertThat(notice.json().has("dpoContact")).isFalse();
    assertThat(notice.json().has("rightsContact")).isFalse();
    assertThat(notice.json().has("processor")).isFalse();
  }
}
