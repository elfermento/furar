// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.CancelledWait;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingWaitCancelledSql} só garda, le e borra as esperas
 * canceladas do seu concello.
 */
class BookingWaitCancelledSqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
  static final UUID CANCELLATION = UUID.randomUUID();

  @Autowired BookingWaitCancelledSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, java.util.function.Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  static CancelledWait cancelled() {
    Instant start = NOW.plus(1, ChronoUnit.DAYS);
    return new CancelledWait(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "ana@example.org",
        Language.GL,
        start,
        start.plus(1, ChronoUnit.HOURS));
  }

  @Override
  protected Object seed(UUID municipality) {
    CancelledWait wait = cancelled();
    as(
        municipality,
        () -> {
          subject.insert(municipality, CANCELLATION, wait, NOW);
          return null;
        });
    return wait.id();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.ofCancellation(municipality, CANCELLATION).stream()
                .<Object>map(CancelledWait::id)
                .toList());
  }

  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("cancelada-a")));
    UUID b = id(createMunicipality(uniqueSlug("cancelada-b")));
    seed(b);

    assertThat(as(a, () -> subject.ofCancellation(b, CANCELLATION))).isEmpty();
    assertThat(as(a, () -> subject.deleteBefore(b, NOW.plusSeconds(60)))).isZero();
    assertThat(as(b, () -> subject.ofCancellation(b, CANCELLATION))).hasSize(1);
    assertThat(as(b, () -> subject.deleteBefore(b, NOW.plusSeconds(60)))).isEqualTo(1);
  }
}
