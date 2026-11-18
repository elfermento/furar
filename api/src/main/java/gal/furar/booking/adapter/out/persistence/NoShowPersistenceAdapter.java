// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.NoShowLedger;
import gal.furar.booking.domain.BookingEventKind;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Faltas e bloqueos no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class NoShowPersistenceAdapter extends TenantScopedPersistenceAdapter implements NoShowLedger {

  private final NoShowBlockSql blocks;
  private final BookingAttendanceSql attendance;
  private final BookingSql bookings;

  NoShowPersistenceAdapter(
      NoShowBlockSql blocks, BookingAttendanceSql attendance, BookingSql bookings) {
    this.blocks = blocks;
    this.attendance = attendance;
    this.bookings = bookings;
  }

  @Override
  public List<BookingId> countingFaults(String emailKey, Instant from) {
    return blocks.countingFaults(tenant().id(), emailKey, from).stream()
        .map(BookingId::new)
        .toList();
  }

  @Override
  public void block(String emailKey, Instant startsAt, Instant endsAt, List<BookingId> faults) {
    blocks.insert(
        tenant().id(),
        Ids.newV7(),
        emailKey,
        startsAt,
        endsAt,
        faults.stream().map(BookingId::value).toList());
  }

  @Override
  public Optional<ActiveBlock> activeBlock(String emailKey, Instant now) {
    return blocks
        .active(tenant().id(), emailKey, now)
        .map(b -> new ActiveBlock(b.endsAt(), b.faults()));
  }

  @Override
  public Set<String> blockedAmong(Collection<String> emailKeys, Instant now) {
    return Set.copyOf(blocks.activeAmong(tenant().id(), emailKeys, now));
  }

  @Override
  public int lift(BookingId cause, Instant now, String actorSubject) {
    return blocks.liftCausedBy(tenant().id(), cause.value(), now, actorSubject);
  }

  @Override
  public List<Marked> markEnded(
      UUID facilityId,
      Instant since,
      Instant endedBy,
      int limit,
      Instant now,
      String actorSubject) {
    UUID m = tenant().id();
    List<UUID> candidates = attendance.noShowCandidates(m, facilityId, since, endedBy, limit);
    return attendance.markNoShow(m, candidates, now, actorSubject).stream()
        .map(
            row ->
                new Marked(
                    new BookingId(row.bookingId()),
                    row.emailKey(),
                    bookings.insertEvent(
                        m, row.bookingId(), BookingEventKind.NO_SHOW, now, actorSubject)))
        .toList();
  }
}
