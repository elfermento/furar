// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.out;

import gal.furar.people.domain.DisplayPreferences;
import java.time.Instant;
import java.util.Optional;

/** As preferencias de accesibilidade de cada conta ({@code account_display_preference}). */
public interface DisplayPreferenceRepository {

  /** As da conta, se as gardou algunha vez. */
  Optional<DisplayPreferences> find(String subject);

  /** Garda as da conta (substitúe as que houbese). */
  void save(String subject, DisplayPreferences preferences, Instant now);
}
