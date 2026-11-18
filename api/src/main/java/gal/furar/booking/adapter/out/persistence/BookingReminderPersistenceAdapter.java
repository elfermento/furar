// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.BookingReminders;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Recordatorios no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class BookingReminderPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements BookingReminders {

  private final BookingReminderSql sql;

  BookingReminderPersistenceAdapter(BookingReminderSql sql) {
    this.sql = sql;
  }

  @Override
  public List<Marked> markDue(Instant now, Duration lead, int limit) {
    UUID municipality = tenant().id();
    return sql.mark(municipality, sql.due(municipality, now, lead, limit), now);
  }

  @Override
  public Set<String> optedOutAmong(Collection<String> emailKeys) {
    return sql.optedOutAmong(tenant().id(), emailKeys);
  }

  @Override
  public boolean optedOut(String emailKey) {
    return !sql.optedOutAmong(tenant().id(), List.of(emailKey)).isEmpty();
  }

  @Override
  public void optOut(String emailKey, Instant now) {
    sql.optOut(tenant().id(), emailKey, now);
  }

  @Override
  public void optIn(String emailKey) {
    sql.optIn(tenant().id(), emailKey);
  }

  @Override
  public Optional<String> holderEmailKey(UUID bookingId) {
    return sql.holderEmailKey(tenant().id(), bookingId);
  }
}
