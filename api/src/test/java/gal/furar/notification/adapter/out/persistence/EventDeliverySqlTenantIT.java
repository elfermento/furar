// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.EventMailDelivery;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** A8b (ADR-0006): {@link EventDeliverySql} só le e escribe os envíos do seu concello. */
class EventDeliverySqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
  static final UUID EVENT = UUID.randomUUID();

  @Autowired EventDeliverySql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  String sent(UUID municipality, String key) {
    as(
        municipality,
        () -> {
          subject.insert(
              municipality,
              new EventMailDelivery(
                  EVENT,
                  EventMailDelivery.Kind.EVENT_CANCELLED,
                  key,
                  "persoa@example.org",
                  Language.GL,
                  DeliveryStatus.SENT,
                  null,
                  NOW),
              NOW);
          return null;
        });
    return key;
  }

  @Override
  protected Object seed(UUID municipality) {
    return sent(municipality, UUID.randomUUID().toString());
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            List.<Object>copyOf(
                subject.keys(municipality, EVENT, EventMailDelivery.Kind.EVENT_CANCELLED)));
  }

  @Test
  void keysAreOnlyItsOwn() {
    UUID a = id(createMunicipality(uniqueSlug("evd-a")));
    UUID b = id(createMunicipality(uniqueSlug("evd-b")));
    String mine = sent(a, "k-a");
    sent(b, "k-b");
    assertThat(as(a, () -> subject.keys(a, EVENT, EventMailDelivery.Kind.EVENT_CANCELLED)))
        .containsExactly(mine);
    assertThat(as(a, () -> subject.keys(a, EVENT, EventMailDelivery.Kind.EVENT_CHANGED))).isEmpty();
  }
}
