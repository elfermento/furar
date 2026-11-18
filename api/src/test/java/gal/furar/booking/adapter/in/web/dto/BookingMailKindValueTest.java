// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.BookingMailLog;
import org.junit.jupiter.api.Test;

/** Cada correo que rexistra o detalle da reserva sae na API co mesmo nome. */
class BookingMailKindValueTest {

  @Test
  void everyLoggedMailHasItsApiValue() {
    for (BookingMailLog.Kind kind : BookingMailLog.Kind.values()) {
      assertThat(BookingMailKindValue.valueOf(kind.name()).name()).isEqualTo(kind.name());
    }
  }
}
