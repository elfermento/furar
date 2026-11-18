// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.application.port.in.DisplayPreferencesUseCase;
import gal.furar.people.application.port.out.DisplayPreferenceRepository;
import gal.furar.people.domain.DisplayPreferences;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As preferencias de accesibilidade da conta da sesión (ADR-0031, revisión do 2026-11-11). Só as da
 * propia conta, polo {@code sub} do token. Non van ao rexistro de actividade nin ao rexistro
 * técnico: poden revelar unha discapacidade.
 */
@Service
public class DisplayPreferencesService implements DisplayPreferencesUseCase {

  private final DisplayPreferenceRepository preferences;
  private final CurrentActor currentActor;
  private final Clock clock;

  public DisplayPreferencesService(
      DisplayPreferenceRepository preferences, CurrentActor currentActor, Clock clock) {
    this.preferences = preferences;
    this.currentActor = currentActor;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public Optional<DisplayPreferences> mine() {
    return preferences.find(subject());
  }

  @Override
  @Transactional
  @PersonUseCase
  public void save(DisplayPreferences chosen) {
    preferences.save(subject(), chosen, clock.instant());
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }
}
