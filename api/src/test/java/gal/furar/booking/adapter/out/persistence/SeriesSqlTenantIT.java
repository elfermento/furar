// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link SeriesSql} só le, garda e cancela as tempadas e as sesións
 * do seu concello, coa RLS da transacción e co concello como parámetro.
 */
class SeriesSqlTenantIT extends AbstractTenantIT {

  @Autowired SeriesSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    UUID organization = SeasonSeeds.organization(jdbc, municipality, "Club " + UUID.randomUUID());
    return SeasonSeeds.series(
        jdbc, municipality, organization, SeasonSeeds.place(jdbc, municipality));
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.list(municipality, false, LocalDate.now()).stream()
                .<Object>map(s -> s.id())
                .toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("tempada-a")));
    UUID b = id(createMunicipality(uniqueSlug("tempada-b")));
    UUID organization = SeasonSeeds.organization(jdbc, b, "Club alleo");
    SeasonSeeds.Place place = SeasonSeeds.place(jdbc, b);
    UUID theirs = SeasonSeeds.series(jdbc, b, organization, place);
    Instant now = Instant.now();

    assertThat(as(a, () -> subject.find(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.findForUpdate(b, theirs))).isEmpty();
    assertThat(as(a, () -> subject.findByKey(b, "key-" + theirs))).isEmpty();
    assertThat(as(a, () -> subject.ofOrganization(b, organization))).isEmpty();
    assertThat(as(a, () -> subject.counts(b, List.of(theirs)))).isEmpty();
    assertThat(as(a, () -> subject.sessionDates(b, theirs))).isEmpty();
    assertThat(
            as(a, () -> subject.occupants(b, place.facility(), now, now.plusSeconds(400 * 86400L))))
        .isEmpty();
    assertThat(as(a, () -> subject.confirmedSessions(b, theirs, now))).isEmpty();
    assertThat(as(a, () -> subject.cancelBookings(b, List.of(theirs), now, "sub-x"))).isZero();
    assertThat(as(a, () -> subject.deactivateOccupancies(b, List.of(theirs)))).isZero();
    assertThat(as(b, () -> subject.find(b, theirs))).isPresent();
  }
}
