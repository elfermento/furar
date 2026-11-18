// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.application.port.out.EventDeliveryLog;
import gal.furar.notification.domain.EventMailDelivery;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Rexistro dos correos dos eventos no concello actual: falla sen concello (ADR-0006). */
@Component
class EventDeliveryLogAdapter extends TenantScopedPersistenceAdapter implements EventDeliveryLog {

  private final EventDeliverySql sql;
  private final Clock clock;

  EventDeliveryLogAdapter(EventDeliverySql sql, Clock clock) {
    this.sql = sql;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> keys(UUID eventId, EventMailDelivery.Kind kind) {
    return Set.copyOf(sql.keys(tenant().id(), eventId, kind));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(EventMailDelivery delivery) {
    sql.insert(tenant().id(), delivery, clock.instant());
  }
}
