// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.season.Organization;
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

/**
 * A8b (ADR-0006): o SQL nativo de {@link OrganizationSql} só le, garda e borra as entidades e os
 * responsables do seu concello, coa RLS da transacción e co concello como parámetro.
 */
class OrganizationSqlTenantIT extends AbstractTenantIT {

  @Autowired OrganizationSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    return SeasonSeeds.organization(jdbc, municipality, "Club " + UUID.randomUUID());
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.list(municipality, false, Instant.now()).stream()
                .<Object>map(l -> l.head().id())
                .toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("entidade-a")));
    UUID b = id(createMunicipality(uniqueSlug("entidade-b")));
    UUID theirs = SeasonSeeds.organization(jdbc, b, "Club alleo");
    Organization.Manager manager =
        new Organization.Manager("ana@example.org", "ana@example.org", Instant.now(), "sub-x");

    assertThat(as(a, () -> subject.find(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.findForUpdate(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.findForShare(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.byIds(b, List.of(theirs)))).isEmpty();
    assertThat(as(a, () -> subject.hasBookings(b, theirs))).isFalse();
    assertThat(as(a, () -> subject.hasSeries(b, theirs))).isFalse();
    assertThat(as(a, () -> subject.hasFutureBookings(b, theirs, Instant.now()))).isFalse();
    assertThat(as(a, () -> subject.renameBookings(b, theirs, "Outro"))).isZero();
    assertThat(as(a, () -> subject.delete(b, theirs))).isZero();
    assertThat(as(a, () -> subject.deleteManagers(b, theirs))).isZero();
    // En b segue e cos seus responsables.
    as(
        b,
        () -> {
          subject.insertManager(b, theirs, manager);
          return null;
        });
    assertThat(as(a, () -> subject.managers(b, List.of(theirs)))).isEmpty();
    assertThat(as(a, () -> subject.ofManager(b, "ana@example.org"))).isEmpty();
    assertThat(as(b, () -> subject.ofManager(b, "ana@example.org"))).containsExactly(theirs);
  }
}
