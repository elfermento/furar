// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.BookingWaits;
import gal.furar.booking.domain.CancelledWait;
import gal.furar.booking.domain.Wait;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Esperas no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class BookingWaitPersistenceAdapter extends TenantScopedPersistenceAdapter implements BookingWaits {

  private final BookingWaitSql sql;
  private final BookingWaitTallySql tally;
  private final BookingWaitCancelledSql cancelled;

  BookingWaitPersistenceAdapter(
      BookingWaitSql sql, BookingWaitTallySql tally, BookingWaitCancelledSql cancelled) {
    this.sql = sql;
    this.tally = tally;
    this.cancelled = cancelled;
  }

  @Override
  public boolean insert(Wait wait, int ordinal) {
    return sql.insert(tenant().id(), wait, ordinal);
  }

  @Override
  public Optional<Wait> findOfAccount(String subject, UUID useId, Instant start, Instant now) {
    return sql.findOfAccount(tenant().id(), subject, useId, start, now);
  }

  @Override
  public List<Wait> activeOfAccount(String subject, Instant now) {
    return sql.activeOfAccount(tenant().id(), subject, now);
  }

  @Override
  public boolean delete(UUID id, String subject) {
    return sql.delete(tenant().id(), id, subject) > 0;
  }

  @Override
  public int refreshEmail(String subject, String emailKey) {
    return sql.refreshEmail(tenant().id(), subject, emailKey);
  }

  @Override
  public int deleteServed(String emailKey, UUID useId, Instant start, Instant end) {
    return sql.deleteServed(tenant().id(), emailKey, useId, start, end);
  }

  @Override
  public List<Wait> activeOfFacility(UUID facilityId, Instant from, Instant to, Instant now) {
    return sql.activeOfFacility(tenant().id(), facilityId, from, to, now);
  }

  @Override
  public int deleteAll(Collection<UUID> ids) {
    return sql.deleteAll(tenant().id(), ids);
  }

  @Override
  public int cancel(Collection<UUID> ids, UUID cancellationId, Instant now) {
    UUID municipality = tenant().id();
    List<Wait> gone = sql.deleteAllReturning(municipality, ids);
    for (Wait w : gone) {
      cancelled.insert(
          municipality,
          cancellationId,
          new CancelledWait(w.id(), w.facilityId(), w.emailKey(), w.language(), w.start(), w.end()),
          now);
    }
    return gone.size();
  }

  @Override
  public List<CancelledWait> cancelled(UUID cancellationId) {
    return cancelled.ofCancellation(tenant().id(), cancellationId);
  }

  @Override
  public int purgeCancelledBefore(Instant cutoff) {
    return cancelled.deleteBefore(tenant().id(), cutoff);
  }

  @Override
  public List<Wait> ofSlot(UUID useId, Instant start, Instant createdUntil, Instant now) {
    return sql.ofSlot(tenant().id(), useId, start, createdUntil, now);
  }

  @Override
  public int expire(Instant now) {
    return tally(sql.deletePast(tenant().id(), now));
  }

  @Override
  public int expireOfAccount(String subject, Instant now) {
    return tally(sql.deletePastOfAccount(tenant().id(), subject, now));
  }

  /** Suma ao reconto anónimo as esperas que se acaban de borrar, por uso e franxa. */
  private int tally(List<BookingWaitSql.Gone> gone) {
    UUID municipality = tenant().id();
    // En orde fixa: dúas pasadas que se crucen suman as mesmas filas na mesma orde.
    Comparator<BookingWaitSql.Gone> order =
        Comparator.comparing(BookingWaitSql.Gone::useId).thenComparing(BookingWaitSql.Gone::start);
    gone.stream()
        .collect(
            Collectors.groupingBy(
                Function.identity(), () -> new TreeMap<>(order), Collectors.counting()))
        .forEach(
            (slot, waits) ->
                tally.add(
                    municipality, slot.facilityId(), slot.useId(), slot.start(), waits.intValue()));
    return gone.size();
  }

  @Override
  public int purgeTallyBefore(Instant cutoff) {
    return tally.deleteBefore(tenant().id(), cutoff);
  }
}
