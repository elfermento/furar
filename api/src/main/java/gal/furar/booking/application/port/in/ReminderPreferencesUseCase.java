// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

/**
 * Os recordatorios por correo dunha persoa nun concello (ADR-0035): darse de baixa desde a ligazón
 * do propio correo, sen sesión, e velos ou cambialos desde a súa conta. Van polo correo (a mesma
 * clave ca a reserva), así que valen tamén para as reservas do mostrador a nome dun correo sen
 * conta.
 */
public interface ReminderPreferencesUseCase {

  /**
   * Desactiva os recordatorios do correo da reserva da ligazón neste concello. Repetir non cambia
   * nada.
   *
   * @throws gal.furar.shared.domain.DomainException {@code booking.reminder-link-invalid} ou {@code
   *     booking.reminder-link-expired}
   */
  void optOut(String token);

  /** Se o correo verificado da sesión recibe recordatorios neste concello. */
  boolean remindersEnabled();

  /** Acende ou apaga os recordatorios do correo verificado da sesión neste concello. */
  void setRemindersEnabled(boolean enabled);
}
