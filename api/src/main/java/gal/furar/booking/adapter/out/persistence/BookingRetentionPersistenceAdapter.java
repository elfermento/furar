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
  private final BookingParticipantSql participants;
  private final NoShowBlockSql blocks;

  BookingRetentionPersistenceAdapter(
      BookingRetentionSql sql, BookingParticipantSql participants, NoShowBlockSql blocks) {
    this.sql = sql;
    this.participants = participants;
    this.blocks = blocks;
  }

  @Override
  public List<BookingId> expired(boolean paid, Instant cutoff, int limit) {
    UUID tenant = tenant().id();
    List<UUID> ids =
        paid ? sql.expiredPaid(tenant, cutoff, limit) : sql.expiredUnpaid(tenant, cutoff, limit);
    return ids.stream().map(BookingId::new).toList();
  }

  @Override
  public List<BookingId> unpaidOf(String holderSubject, int limit) {
    return sql.unpaidOf(tenant().id(), holderSubject, limit).stream().map(BookingId::new).toList();
  }

  @Override
  public int purgeNoShowBlocks(Instant cutoff, int limit) {
    UUID m = tenant().id();
    return blocks.delete(m, blocks.endedBefore(m, cutoff, limit));
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
    // As da entidade non teñen conta de titular: o que fixo a persoa responsable (ADR-0039).
    sql.forgetOrganizationEventActors(tenant, values);
    sql.forgetOrganizationPeople(tenant, values);
    // As persoas da reserva: sen nome nin ano, e a ficha queda solta (ADR-0031, punto 6).
    participants.anonymise(tenant, values);
    return sql.forgetHolder(tenant, values, now);
  }
}
