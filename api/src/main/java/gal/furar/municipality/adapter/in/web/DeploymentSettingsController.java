// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.DeploymentSettingsResponse;
import gal.furar.municipality.adapter.in.web.dto.MapSettingsResponse;
import gal.furar.municipality.application.port.in.GetDeploymentSettingsUseCase;
import gal.furar.shared.domain.MapSettings;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración pública do despregue (sen concello no URL, como a cláusula informativa). */
@RestController
@Tag(name = "deployment", description = "Despregue")
class DeploymentSettingsController {

  private final GetDeploymentSettingsUseCase settings;

  DeploymentSettingsController(GetDeploymentSettingsUseCase settings) {
    this.settings = settings;
  }

  @GetMapping("/api/v1/deployment-settings")
  @PublicEndpoint
  @Operation(
      operationId = "getDeploymentSettings",
      summary = "Datos do despregue para a web: se hai mapa e o seu recoñecemento")
  DeploymentSettingsResponse get() {
    MapSettings map = settings.map();
    return new DeploymentSettingsResponse(
        new MapSettingsResponse(
            map.enabled(),
            map.attribution(),
            map.attributionUrl().map(URI::toString).orElse(null)));
  }
}
