// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gal.furar.municipality.application.port.in.GetDeploymentSettingsUseCase;
import gal.furar.shared.domain.MapSettings;
import gal.furar.shared.security.config.WebTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Datos do despregue (ADR-0023): públicos, sen sesión nin concello no URL. */
@WebMvcTest(DeploymentSettingsController.class)
@Import(WebTestConfig.class)
@ActiveProfiles("test")
class DeploymentSettingsWebTest {

  @Autowired MockMvc mvc;
  @MockitoBean GetDeploymentSettingsUseCase settings;

  @Test
  void anyoneReadsTheMapAndItsAttribution() throws Exception {
    when(settings.map())
        .thenReturn(MapSettings.parse("on", "CC BY 4.0 scne.es", "https://www.scne.es"));
    mvc.perform(get("/api/v1/deployment-settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.map.enabled").value(true))
        .andExpect(jsonPath("$.map.attribution").value("CC BY 4.0 scne.es"))
        .andExpect(jsonPath("$.map.attributionUrl").value("https://www.scne.es"));
  }

  @Test
  void withoutMapTheWebKnowsIt() throws Exception {
    when(settings.map()).thenReturn(MapSettings.parse("off", "", ""));
    mvc.perform(get("/api/v1/deployment-settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.map.enabled").value(false))
        .andExpect(jsonPath("$.map.attributionUrl").doesNotExist());
  }
}
