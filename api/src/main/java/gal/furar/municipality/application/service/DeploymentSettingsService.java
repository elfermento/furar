// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.in.GetDeploymentSettingsUseCase;
import gal.furar.shared.domain.JoinRequestContact;
import gal.furar.shared.domain.MapSettings;
import gal.furar.shared.domain.SupportContact;
import gal.furar.shared.security.PublicUseCase;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Configuración pública do despregue: a web precisa sabela antes de pintar un mapa e para mostrar o
 * correo de soporte no pé, e se hai «¿Es un concello?» (sen o correo de destino).
 */
@Service
public class DeploymentSettingsService implements GetDeploymentSettingsUseCase {

  private final MapSettings map;
  private final SupportContact support;
  private final JoinRequestContact joinRequest;

  public DeploymentSettingsService(
      MapSettings map, SupportContact support, JoinRequestContact joinRequest) {
    this.map = map;
    this.support = support;
    this.joinRequest = joinRequest;
  }

  @Override
  @PublicUseCase
  public MapSettings map() {
    return map;
  }

  @Override
  @PublicUseCase
  public Optional<String> supportEmail() {
    return support.email();
  }

  @Override
  @PublicUseCase
  public boolean joinRequests() {
    return joinRequest.enabled();
  }
}
