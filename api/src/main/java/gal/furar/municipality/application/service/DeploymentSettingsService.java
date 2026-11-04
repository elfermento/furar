// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.in.GetDeploymentSettingsUseCase;
import gal.furar.shared.domain.MapSettings;
import gal.furar.shared.security.PublicUseCase;
import org.springframework.stereotype.Service;

/** Configuración pública do despregue: a web precisa sabela antes de pintar un mapa. */
@Service
public class DeploymentSettingsService implements GetDeploymentSettingsUseCase {

  private final MapSettings map;

  public DeploymentSettingsService(MapSettings map) {
    this.map = map;
  }

  @Override
  @PublicUseCase
  public MapSettings map() {
    return map;
  }
}
