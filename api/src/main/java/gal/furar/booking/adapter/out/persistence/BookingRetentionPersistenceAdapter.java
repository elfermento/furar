// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.BookingRetention;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Conservación das reservas no concello actual: falla sen concello ou sen transacción. */
@Component
class BookingRetentionPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements BookingRetention {

  private final BookingRetentionSql sql;

  BookingRetentionPersistenceAdapter(BookingRetentionSql sql) {
    this.sql = sql;
  }

  @Override
  public List<BookingId> expired(boolean paid, Instant cutoff, int limit) {
    UUID tenant = tenant().id();
    List<UUID> ids =
        paid ? sql.expiredPaid(tenant, cutoff, limit) : sql.expiredUnpaid(tenant, cutoff, limit);
    return ids.stream().map(BookingId::new).toList();
  }

  @Override
  public int anonymise(Collection<BookingId> ids, Instant now) {
    if (ids.isEmpty()) {
      return 0;
    }
    UUID tenant = tenant().id();
    List<UUID> values = ids.stream().map(BookingId::value).toList();
    // Antes de borrar a conta: as dúas primeiras compárana coa de quen actuou.
    sql.forgetEventActors(tenant, values);
    sql.forgetCanceller(tenant, values);
    return sql.forgetHolder(tenant, values, now);
  }
}
