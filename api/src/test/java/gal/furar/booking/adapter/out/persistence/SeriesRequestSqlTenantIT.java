// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link SeriesRequestSql} só le, garda e borra as solicitudes de
 * tempada do seu concello, coa RLS da transacción e co concello como parámetro.
 */
class SeriesRequestSqlTenantIT extends AbstractTenantIT {

  @Autowired SeriesRequestSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    UUID organization = SeasonSeeds.organization(jdbc, municipality, "Club " + UUID.randomUUID());
    return SeasonSeeds.request(
        jdbc, municipality, organization, SeasonSeeds.place(jdbc, municipality));
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () -> subject.list(municipality, "PENDING").stream().<Object>map(r -> r.id()).toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("solicitude-a")));
    UUID b = id(createMunicipality(uniqueSlug("solicitude-b")));
    UUID organization = SeasonSeeds.organization(jdbc, b, "Club alleo");
    UUID theirs = SeasonSeeds.request(jdbc, b, organization, SeasonSeeds.place(jdbc, b));

    assertThat(as(a, () -> subject.find(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.findForUpdate(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.ofOrganization(b, organization))).isEmpty();
    assertThat(as(a, () -> subject.countPending(b))).isZero();
    assertThat(as(a, () -> subject.countPending(b, organization))).isZero();
    assertThat(as(a, () -> subject.delete(b, theirs))).isZero();
    assertThat(as(a, () -> subject.deletePending(b, organization))).isZero();
    assertThat(as(a, () -> subject.purgeDecidedBefore(b, Instant.now().plusSeconds(86400), 10)))
        .isZero();
    assertThat(as(b, () -> subject.countPending(b, organization))).isEqualTo(1);
  }
}
