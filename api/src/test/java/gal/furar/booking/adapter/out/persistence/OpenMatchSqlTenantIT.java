// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.port.out.OpenMatches.Listed;
import gal.furar.booking.domain.OpenMatch;
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
 * A8b (ADR-0006): o SQL nativo de {@link OpenMatchSql} só le, garda, conta e borra as partidas
 * abertas do seu concello, coa RLS da transacción e co concello como parámetro.
 */
class OpenMatchSqlTenantIT extends AbstractTenantIT {

  @Autowired OpenMatchSql subject;
  @Autowired TransactionTemplate tx;

  private int seeded;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    return OpenMatchSeeds.match(jdbc, municipality, "MQ", seeded++).match();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.visible(municipality, Instant.now(), null, 50, 0).stream()
                .<Object>map(l -> l.match().id())
                .toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("partida-a")));
    UUID b = id(createMunicipality(uniqueSlug("partida-b")));
    OpenMatchSeeds.Seeded theirs = OpenMatchSeeds.match(jdbc, b, "MQ", seeded++);
    Instant now = Instant.now();

    assertThat(as(a, () -> subject.find(b, theirs.match()))).isEmpty();
    assertThat(as(a, () -> subject.findLocked(b, theirs.match()))).isEmpty();
    assertThat(as(a, () -> subject.findByBookingLocked(b, theirs.booking()))).isEmpty();
    assertThat(as(a, () -> subject.listed(b, theirs.match()))).isEmpty();
    assertThat(as(a, () -> subject.ofBookings(b, List.of(theirs.booking())))).isEmpty();
    assertThat(as(a, () -> subject.countVisible(b, now, null))).isZero();
    assertThat(as(a, () -> subject.ofAccount(b, "sub-ana", now))).isEmpty();
    assertThat(
            as(
                a,
                () ->
                    subject.deadlineDue(
                        b, now.plusSeconds(3 * 86400), now.minusSeconds(86400), 1, 10)))
        .isEmpty();
    assertThat(as(a, () -> subject.purgeOfBookings(b, List.of(theirs.booking())))).isZero();
    OpenMatch match = as(b, () -> subject.find(b, theirs.match()).orElseThrow());
    assertThat(as(a, () -> subject.update(b, match.version(), match.close(now)))).isZero();
    // En b segue aberta e visible.
    assertThat(as(b, () -> subject.listed(b, theirs.match())))
        .get()
        .extracting(Listed::confirmed, l -> l.match().status().name())
        .containsExactly(0, "OPEN");
  }

  @Test
  void countsOnlyTheConfirmedAndListsOnlyThoseWithRoom() {
    UUID m = id(createMunicipality(uniqueSlug("partida-contas")));
    OpenMatchSeeds.Seeded match = OpenMatchSeeds.match(jdbc, m, "MQ", seeded++);
    OpenMatchSeeds.player(jdbc, m, match.match(), "sub-xan", "CONFIRMED");
    OpenMatchSeeds.player(jdbc, m, match.match(), "sub-bea", "PENDING");
    OpenMatchSeeds.player(jdbc, m, match.match(), "sub-rui", "REJECTED");
    Instant now = Instant.now();

    Listed listed = as(m, () -> subject.listed(m, match.match()).orElseThrow());
    assertThat(listed.confirmed()).isEqualTo(1);
    assertThat(listed.pending()).isEqualTo(1);
    assertThat(as(m, () -> subject.ofAccount(m, "sub-bea", now))).hasSize(1);
    assertThat(as(m, () -> subject.ofAccount(m, "sub-ninguen", now))).isEmpty();

    // 2 + 1 de 6: con sitio. Con 4 máis dentro (2 + 5 > 6 non pode ser; 2 + 4 = 6), completa.
    for (int i = 0; i < 3; i++) {
      OpenMatchSeeds.player(jdbc, m, match.match(), "sub-mais-" + i, "CONFIRMED");
    }
    assertThat(as(m, () -> subject.countVisible(m, now, null))).isZero();
    assertThat(as(m, () -> subject.listed(m, match.match()).orElseThrow().confirmed()))
        .isEqualTo(4);
  }
}
