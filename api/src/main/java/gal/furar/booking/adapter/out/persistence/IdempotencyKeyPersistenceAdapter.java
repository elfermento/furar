// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.IdempotencyKeys;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Claves de reintento no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class IdempotencyKeyPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements IdempotencyKeys {

  private final IdempotencyKeySql sql;

  IdempotencyKeyPersistenceAdapter(IdempotencyKeySql sql) {
    this.sql = sql;
  }

  @Override
  public Optional<Remembered> find(String subject, IdempotencyKey key) {
    return sql.find(tenant().id(), subject, key.value());
  }

  @Override
  public void remember(
      String subject, IdempotencyKey key, String fingerprint, BookingId bookingId, Instant at) {
    try {
      sql.insert(tenant().id(), subject, key.value(), fingerprint, bookingId.value(), at);
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(e.getMostSpecificCause().getMessage())
          .contains("booking_idempotency_key_uk")) {
        throw new DomainException(BookingError.IDEMPOTENCY_KEY_REUSED);
      }
      throw e;
    }
  }

  @Override
  public int purgeCreatedBefore(Instant cutoff) {
    return sql.deleteCreatedBefore(tenant().id(), cutoff);
  }
}
