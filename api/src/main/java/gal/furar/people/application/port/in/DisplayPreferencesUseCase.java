// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.in;

import gal.furar.people.domain.DisplayPreferences;
import java.util.Optional;

/** As preferencias de accesibilidade da conta da sesión (ADR-0031, revisión do 2026-11-11). */
public interface DisplayPreferencesUseCase {

  /** As gardadas; baleiro se nunca se gardaron. */
  Optional<DisplayPreferences> mine();

  /** Garda as da conta. */
  void save(DisplayPreferences preferences);
}
