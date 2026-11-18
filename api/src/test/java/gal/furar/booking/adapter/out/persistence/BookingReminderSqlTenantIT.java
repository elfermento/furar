// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingReminderSql} só le, marca e dá de baixa no seu
 * concello, coa RLS da transacción e co concello como parámetro. O mesmo correo pode non querer
 * recordatorios nun concello e si noutro.
 */
class BookingReminderSqlTenantIT extends AbstractTenantIT {

  static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(6 * 3600);

  private final List<AttendanceSeeds.Seeded> seeded = new ArrayList<>();

  @Autowired BookingReminderSql subject;
  @Autowired TransactionTemplate tx;

  /** Unha reserva á que xa lle toca o recordatorio (feita hai días). */
  AttendanceSeeds.Seeded insert(UUID municipality) {
    AttendanceSeeds.Seeded row =
        AttendanceSeeds.booking(jdbc, municipality, START, "NONE", "RE", seeded.size());
    jdbc.sql("update booking set created_at = :c where id = :id")
        .param("c", OffsetDateTime.now(ZoneOffset.UTC).minusDays(3))
        .param("id", row.booking())
        .update();
    seeded.add(row);
    return row;
  }

  <T> T as(UUID municipality, java.util.function.Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    return insert(municipality).booking();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            seeded.stream()
                .filter(row -> subject.holderEmailKey(municipality, row.booking()).isPresent())
                .<Object>map(AttendanceSeeds.Seeded::booking)
                .toList());
  }

  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("lembra-a")));
    UUID b = id(createMunicipality(uniqueSlug("lembra-b")));
    AttendanceSeeds.Seeded theirs = insert(b);
    Instant now = Instant.now();
    Duration lead = Duration.ofHours(24);

    assertThat(as(a, () -> subject.due(b, now, lead, 10))).isEmpty();
    assertThat(as(a, () -> subject.mark(b, List.of(theirs.booking()), now))).isEmpty();
    assertThat(as(a, () -> subject.holderEmailKey(b, theirs.booking()))).isEmpty();
    as(
        b,
        () -> {
          subject.optOut(b, AttendanceSeeds.EMAIL, now);
          return null;
        });
    assertThat(as(a, () -> subject.optedOutAmong(b, List.of(AttendanceSeeds.EMAIL)))).isEmpty();
    assertThat(as(a, () -> subject.optIn(b, AttendanceSeeds.EMAIL))).isZero();
    assertThat(as(a, () -> subject.optedOutAmong(a, List.of(AttendanceSeeds.EMAIL)))).isEmpty();

    // No seu concello: toca, márcase unha vez e a baixa vese.
    assertThat(as(b, () -> subject.due(b, now, lead, 10))).contains(theirs.booking());
    assertThat(as(b, () -> subject.mark(b, List.of(theirs.booking()), now))).hasSize(1);
    assertThat(as(b, () -> subject.mark(b, List.of(theirs.booking()), now))).isEmpty();
    assertThat(as(b, () -> subject.optedOutAmong(b, List.of(AttendanceSeeds.EMAIL))))
        .containsExactly(AttendanceSeeds.EMAIL);
  }
}
