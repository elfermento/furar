// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI cara a {@code notification}: o último correo que saíu dunha reserva, para o detalle do
 * persoal («Confirmación enviada hoxe ás 10:42»). Implementaa {@code notification}, que xa depende
 * de {@code booking}; {@code booking} non pode depender dela (ciclo, ADR-0005). Un só bean; sen
 * ningún, {@link NoBookingMailLog}.
 */
public interface BookingMailLog {

  /** O último correo enviado desta reserva do concello actual, se saíu algún. */
  Optional<SentMail> lastSent(UUID bookingId);

  /**
   * Un correo que saíu.
   *
   * @param kind que correo
   * @param at cando saíu
   */
  record SentMail(Kind kind, Instant at) {

    public SentMail {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(at, "at");
    }
  }

  /** Os correos dunha reserva. */
  enum Kind {
    CONFIRMED,
    CHANGED,
    CANCELLED_BY_PERSON,
    CANCELLED_BY_MUNICIPALITY
  }
}
