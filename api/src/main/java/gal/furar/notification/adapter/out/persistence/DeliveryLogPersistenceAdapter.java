// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.IncidentMailDelivery;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import gal.furar.notification.domain.OpenMatchDelivery;
import gal.furar.notification.domain.SeriesDelivery;
import gal.furar.notification.domain.WaitDelivery;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Rexistro de correos no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class DeliveryLogPersistenceAdapter extends TenantScopedPersistenceAdapter implements DeliveryLog {

  private final NotificationDeliverySql sql;
  private final Clock clock;

  DeliveryLogPersistenceAdapter(NotificationDeliverySql sql, Clock clock) {
    this.sql = sql;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Delivery> find(UUID bookingId, MailKind kind, String eventId) {
    return sql.find(tenant().id(), bookingId, kind, eventId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Sent> lastSent(UUID bookingId) {
    return sql.lastSent(tenant().id(), bookingId).map(d -> new Sent(d.kind(), d.at()));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(Delivery delivery) {
    UUID municipality = tenant().id();
    if (sql.update(municipality, delivery, clock.instant()) == 0) {
      sql.insert(municipality, delivery, clock.instant());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, WaitDelivery> waitDeliveries(
      MailKind kind, UUID releaseId, Collection<UUID> waitIds) {
    return sql.waitDeliveries(tenant().id(), kind, releaseId, waitIds).stream()
        .collect(Collectors.toMap(WaitDelivery::waitId, Function.identity()));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> waitsNoticedSince(Collection<UUID> waitIds, Instant since) {
    return Set.copyOf(sql.waitsNoticedSince(tenant().id(), waitIds, since));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(WaitDelivery delivery) {
    UUID municipality = tenant().id();
    if (sql.update(municipality, delivery, clock.instant()) == 0) {
      sql.insert(municipality, delivery, clock.instant());
    }
  }

  @Override
  @Transactional
  public int purgeWaitNoticesBefore(Instant cutoff) {
    return sql.deleteWaitNoticesBefore(tenant().id(), cutoff);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DeliveryStatus> invitationStatus(UUID invitationId) {
    return sql.invitationStatus(tenant().id(), invitationId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(InvitationMailDelivery delivery) {
    sql.insert(tenant().id(), delivery, clock.instant());
  }

  @Override
  @Transactional
  public int forgetInvitations(Collection<UUID> invitationIds) {
    return invitationIds.isEmpty() ? 0 : sql.deleteInvitations(tenant().id(), invitationIds);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> incidentKeys(UUID incidentId, IncidentMailDelivery.Kind kind) {
    return Set.copyOf(sql.incidentKeys(tenant().id(), incidentId, kind));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(IncidentMailDelivery delivery) {
    sql.insert(tenant().id(), delivery, clock.instant());
  }

  @Override
  @Transactional
  public int forgetIncidents(Collection<UUID> incidentIds) {
    return incidentIds.isEmpty() ? 0 : sql.deleteIncidents(tenant().id(), incidentIds);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, OpenMatchDelivery> openMatchDeliveries(MailKind kind, String eventId) {
    return sql.openMatchDeliveries(tenant().id(), kind, eventId).stream()
        .collect(Collectors.toMap(OpenMatchDelivery::target, Function.identity()));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(OpenMatchDelivery delivery) {
    UUID municipality = tenant().id();
    if (sql.updateOpenMatch(municipality, delivery, clock.instant()) == 0) {
      sql.insertOpenMatch(municipality, delivery, clock.instant());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SeriesDelivery> seriesDelivery(MailKind kind, UUID target, String eventId) {
    return sql.seriesDelivery(tenant().id(), kind, target, eventId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(SeriesDelivery delivery) {
    UUID municipality = tenant().id();
    if (sql.updateSeries(municipality, delivery, clock.instant()) == 0) {
      sql.insertSeries(municipality, delivery, clock.instant());
    }
  }

  @Override
  @Transactional
  public int purgeCreatedBefore(Instant cutoff) {
    return sql.deleteCreatedBefore(tenant().id(), cutoff);
  }
}
