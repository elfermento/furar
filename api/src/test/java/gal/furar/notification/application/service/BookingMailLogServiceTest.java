// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.BookingMailLog;
import gal.furar.notification.domain.MailKind;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Todo correo dunha reserva ten o seu nome no detalle do persoal: un tipo novo sen el daba un 500
 * ao ler a reserva despois do primeiro recordatorio.
 */
class BookingMailLogServiceTest {

  @Test
  void everyBookingMailKindReachesTheStaffDetail() {
    for (MailKind kind : Arrays.stream(MailKind.values()).filter(MailKind::ofBooking).toList()) {
      assertThat(BookingMailLog.Kind.valueOf(kind.name()).name()).isEqualTo(kind.name());
    }
  }
}
