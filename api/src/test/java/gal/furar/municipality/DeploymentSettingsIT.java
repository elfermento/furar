// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import org.junit.jupiter.api.Test;

/**
 * Datos do despregue para a web (ADR-0023, revisión do 2026-11-03): públicos, sen concello no URL
 * e, sen FURAR_MAP*, o Mapa base do IGN co seu recoñecemento.
 */
class DeploymentSettingsIT extends AbstractIT {

  @Test
  void anyoneReadsTheDefaultMap() {
    Api.Response settings = api().get("/api/v1/deployment-settings").send();
    assertThat(settings.status()).isEqualTo(200);
    assertThat(settings.json().get("map").get("enabled").asBoolean()).isTrue();
    assertThat(settings.json().get("map").get("attribution").asString())
        .isEqualTo("CC BY 4.0 scne.es");
    assertThat(settings.json().get("map").get("attributionUrl").asString())
        .isEqualTo("https://www.scne.es");
  }
}
