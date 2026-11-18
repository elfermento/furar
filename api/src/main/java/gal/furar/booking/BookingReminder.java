// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.util.Objects;

/**
 * O recordatorio dunha reserva tal como o conta o correo (ADR-0035; A19): a reserva e a proba
 * asinada da ligazón «Non quero recordatorios».
 */
public record BookingReminder(BookingNotice notice, String optOutToken) {

  public BookingReminder {
    Objects.requireNonNull(notice, "notice");
    Objects.requireNonNull(optOutToken, "optOutToken");
  }
}
