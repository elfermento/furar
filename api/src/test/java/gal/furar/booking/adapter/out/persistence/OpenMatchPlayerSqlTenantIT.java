// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.OpenMatchPlayer;
import gal.furar.booking.domain.OpenMatchPlayerStatus;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link OpenMatchPlayerSql} só le, garda, decide e borra as
 * persoas das partidas do seu concello, coa RLS da transacción e co concello como parámetro; e
 * «unha persoa, unha vez» sen abortar a transacción.
 */
class OpenMatchPlayerSqlTenantIT extends AbstractTenantIT {

  @Autowired OpenMatchPlayerSql subject;
  @Autowired TransactionTemplate tx;

  private int seeded;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    UUID match = OpenMatchSeeds.match(jdbc, municipality, "MP", seeded++).match();
    return OpenMatchSeeds.player(jdbc, municipality, match, "sub-xan", "CONFIRMED");
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            jdbc
                .sql("select id from booking_open_match where municipality_id = :m")
                .param("m", municipality)
                .query(UUID.class)
                .list()
                .stream()
                .flatMap(match -> subject.ofMatch(municipality, match).stream())
                .<Object>map(OpenMatchPlayer::id)
                .toList());
  }

  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("xogador-a")));
    UUID b = id(createMunicipality(uniqueSlug("xogador-b")));
    UUID match = OpenMatchSeeds.match(jdbc, b, "MP", seeded++).match();
    UUID pending = OpenMatchSeeds.player(jdbc, b, match, "sub-bea", "PENDING");
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    assertThat(as(a, () -> subject.ofMatch(b, match))).isEmpty();
    assertThat(as(a, () -> subject.ofAccount(b, match, "sub-bea"))).isEmpty();
    assertThat(as(a, () -> subject.byId(b, match, pending))).isEmpty();
    assertThat(as(a, () -> subject.decide(b, match, pending, OpenMatchPlayerStatus.CONFIRMED, now)))
        .isZero();
    assertThat(as(a, () -> subject.leave(b, match, "sub-bea"))).isZero();
    assertThat(as(a, () -> subject.deletePending(b, match))).isZero();
    assertThat(as(b, () -> subject.byId(b, match, pending)))
        .get()
        .extracting(OpenMatchPlayer::status)
        .isEqualTo(OpenMatchPlayerStatus.PENDING);
  }

  @Test
  void onePersonOnceAndOnlyPendingRequestsAreDecided() {
    UUID m = id(createMunicipality(uniqueSlug("xogador-unha-vez")));
    UUID match = OpenMatchSeeds.match(jdbc, m, "MP", seeded++).match();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    OpenMatchPlayer bea =
        OpenMatchPlayer.join(
            UUID.randomUUID(), match, "sub-bea", "bea@example.org", "Bea", Language.ES, true, now);

    assertThat(as(m, () -> subject.insert(m, bea))).isTrue();
    OpenMatchPlayer again =
        OpenMatchPlayer.join(
            UUID.randomUUID(), match, "sub-bea", "bea@example.org", "Bea", Language.ES, true, now);
    assertThat(as(m, () -> subject.insert(m, again))).isFalse();
    assertThat(as(m, () -> subject.decide(m, match, bea.id(), OpenMatchPlayerStatus.REJECTED, now)))
        .isEqualTo(1);
    assertThat(
            as(m, () -> subject.decide(m, match, bea.id(), OpenMatchPlayerStatus.CONFIRMED, now)))
        .as("xa decidida")
        .isZero();
    OpenMatchSeeds.player(jdbc, m, match, "sub-pide", "PENDING");
    assertThat(as(m, () -> subject.deletePending(m, match))).isEqualTo(1);
    assertThat(as(m, () -> subject.ofMatch(m, match)))
        .extracting(OpenMatchPlayer::accountSubject)
        .containsExactly("sub-bea");
  }
}
