// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
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
  @Transactional
  public int purgeCreatedBefore(Instant cutoff) {
    return sql.deleteCreatedBefore(tenant().id(), cutoff);
  }
}
