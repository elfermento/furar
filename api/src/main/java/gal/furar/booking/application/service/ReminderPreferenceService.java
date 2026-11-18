// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.ReminderPreferencesUseCase;
import gal.furar.booking.application.port.out.BookingReminders;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.ReminderOptOutToken;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os recordatorios por correo dunha persoa nun concello (ADR-0035). A baixa desde o correo non pide
 * sesión: a proba asinada da ligazón di de que reserva é, e desa reserva sae o correo; nunca vai no
 * enderezo. Non queda no rexistro de actividade (é do persoal) nin no rexistro técnico co correo.
 */
@Service
public class ReminderPreferenceService implements ReminderPreferencesUseCase {

  private final BookingReminders reminders;
  private final ReminderLinks links;
  private final CurrentActor currentActor;
  private final Clock clock;

  public ReminderPreferenceService(
      BookingReminders reminders, ReminderLinks links, CurrentActor currentActor, Clock clock) {
    this.reminders = reminders;
    this.links = links;
    this.currentActor = currentActor;
    this.clock = clock;
  }

  @Override
  @Transactional
  @PublicUseCase
  public void optOut(String token) {
    ReminderOptOutToken.Check check =
        links.verify(TenantContext.require().id(), token, clock.instant());
    switch (check) {
      case ReminderOptOutToken.Invalid i ->
          throw new DomainException(BookingError.REMINDER_LINK_INVALID);
      case ReminderOptOutToken.Expired e ->
          throw new DomainException(BookingError.REMINDER_LINK_EXPIRED);
      case ReminderOptOutToken.Valid v -> {
        // Unha reserva xa anonimizada non ten correo: a ligazón xa non serve.
        String emailKey =
            reminders
                .holderEmailKey(v.bookingId())
                .orElseThrow(() -> new DomainException(BookingError.REMINDER_LINK_INVALID));
        reminders.optOut(emailKey, clock.instant());
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public boolean remindersEnabled() {
    return !reminders.optedOut(emailKey());
  }

  @Override
  @Transactional
  @PersonUseCase
  public void setRemindersEnabled(boolean enabled) {
    String key = emailKey();
    if (enabled) {
      reminders.optIn(key);
    } else {
      reminders.optOut(key, clock.instant());
    }
  }

  /** O correo verificado da sesión (xa o comprobou {@link PersonUseCase}). */
  private String emailKey() {
    String email =
        currentActor
            .current()
            .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
            .profile()
            .email();
    if (email == null) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    return EmailKey.of(email);
  }
}
