// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * As claves de reintento de «reservar» (ADR-0025, punto 19), sempre no concello actual e dentro
 * dunha transacción (ADR-0006). Cada clave é da persoa que a mandou: a mesma clave doutra persoa é
 * outra clave.
 */
public interface IdempotencyKeys {

  /** O que se gardou cunha clave: a pegada da petición e a reserva que creou. */
  record Remembered(String fingerprint, BookingId bookingId) {

    public Remembered {
      Objects.requireNonNull(fingerprint, "fingerprint");
      Objects.requireNonNull(bookingId, "bookingId");
    }
  }

  /** A reserva creada por esta persoa con esta clave, se aínda se garda. */
  Optional<Remembered> find(String subject, IdempotencyKey key);

  /**
   * Garda a clave coa reserva que acaba de crear, na mesma transacción ca a reserva.
   *
   * @throws DomainException {@code booking.idempotency-key-reused} se a persoa xa a usou (dúas
   *     peticións coa mesma clave e distinta instalación, á vez: non comparten bloqueo)
   */
  void remember(
      String subject, IdempotencyKey key, String fingerprint, BookingId bookingId, Instant at);

  /** Borra as claves gardadas antes de {@code cutoff} e devolve cantas. */
  int purgeCreatedBefore(Instant cutoff);
}
