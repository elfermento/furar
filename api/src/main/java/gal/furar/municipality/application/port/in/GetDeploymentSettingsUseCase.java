// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.shared.domain.MapSettings;

/**
 * Datos do despregue que a web le ao arrincar, sen concello nin sesión (como a cláusula
 * informativa): por agora, o mapa (ADR-0023, revisión do 2026-11-03).
 */
public interface GetDeploymentSettingsUseCase {

  /** Se hai mapa e que recoñecemento leva ({@code FURAR_MAP*}). */
  MapSettings map();
}
