// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link NoShowBlockSql} só le e cambia as faltas e os bloqueos do
 * seu concello, coa RLS da transacción e co concello como parámetro: unha falta nun concello non
 * conta noutro. Co concello alleo como parámetro non ve nin cambia nada.
 */
class NoShowBlockSqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  static final Instant START = NOW.minusSeconds(3 * 3600);
  static final Instant LONG_AGO = NOW.minusSeconds(30L * 86400);
  static final Instant FAR = NOW.plusSeconds(400L * 86400);
  static final String EMAIL = AttendanceSeeds.EMAIL;

  private int seeded;

  @Autowired NoShowBlockSql subject;
  @Autowired TransactionTemplate tx;

  UUID fault(UUID municipality) {
    return AttendanceSeeds.booking(jdbc, municipality, START, "NO_SHOW", "NB", seeded++).booking();
  }

  @Override
  protected Object seed(UUID municipality) {
    return AttendanceSeeds.block(jdbc, municipality, fault(municipality), NOW);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () -> tx.execute(s -> List.<Object>copyOf(subject.endedBefore(municipality, FAR, 1000))));
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("bloqueo-a")));
    UUID b = id(createMunicipality(uniqueSlug("bloqueo-b")));
    UUID loose = fault(b);
    UUID cause = fault(b);
    UUID theirs = AttendanceSeeds.block(jdbc, b, cause, NOW);

    List<Object> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s ->
                        List.<Object>of(
                            subject.countingFaults(b, EMAIL, LONG_AGO).size(),
                            subject.countingFaults(a, EMAIL, LONG_AGO).size(),
                            subject.active(b, EMAIL, NOW).isPresent(),
                            subject.active(a, EMAIL, NOW).isPresent(),
                            subject.liftCausedBy(b, cause, NOW, "sub-staff"),
                            subject.endedBefore(b, FAR, 10).size(),
                            subject.delete(b, List.of(theirs)))));

    assertThat(crossed).containsExactly(0, 0, false, false, 0, 0, 0);
    assertThat(
            jdbc.sql("select (lifted_at is null)::text from booking_no_show_block where id = :id")
                .param("id", theirs)
                .query(String.class)
                .single())
        .as("o bloqueo do outro concello segue como estaba")
        .isEqualTo("true");
    assertThat(loose).isNotNull();
  }

  /** No seu concello: conta as faltas, bloquea, levanta e borra. */
  @Test
  void inItsOwnMunicipalityCountsBlocksLiftsAndDeletes() {
    UUID a = id(createMunicipality(uniqueSlug("bloqueo-c")));
    UUID first = fault(a);
    UUID second = fault(a);
    UUID block = UUID.randomUUID();
    Instant ends = NOW.plusSeconds(15L * 86400);

    List<Object> own =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s -> {
                      List<Object> seen = new ArrayList<>();
                      seen.add(subject.countingFaults(a, EMAIL, LONG_AGO).size());
                      // As que remataron antes da xanela non contan.
                      seen.add(subject.countingFaults(a, EMAIL, NOW).size());
                      seen.add(subject.countingFaults(a, "outra@example.org", LONG_AGO).size());
                      seen.add(subject.active(a, EMAIL, NOW).isPresent());
                      subject.insert(a, block, EMAIL, NOW, ends, List.of(first, second));
                      NoShowBlockSql.Active active = subject.active(a, EMAIL, NOW).orElseThrow();
                      seen.add(active.id());
                      seen.add(active.endsAt());
                      seen.add(active.faults());
                      // As faltas dun bloqueo sen levantar xa non contan.
                      seen.add(subject.countingFaults(a, EMAIL, LONG_AGO).size());
                      seen.add(subject.active(a, EMAIL, ends).isPresent());
                      seen.add(subject.liftCausedBy(a, second, NOW, "sub-staff"));
                      seen.add(subject.liftCausedBy(a, second, NOW, "sub-staff"));
                      seen.add(subject.active(a, EMAIL, NOW).isPresent());
                      // As dun bloqueo levantado volven contar.
                      seen.add(subject.countingFaults(a, EMAIL, LONG_AGO).size());
                      seen.add(subject.endedBefore(a, NOW, 10).size());
                      seen.add(subject.endedBefore(a, FAR, 10));
                      seen.add(subject.delete(a, List.of(block)));
                      seen.add(subject.delete(a, List.of()));
                      return seen;
                    }));

    assertThat(own)
        .containsExactly(
            2, 0, 0, false, block, ends, 2, 0, false, 1, 0, false, 2, 0, List.of(block), 1, 0);
    assertThat(
            jdbc.sql("select count(*) from booking_no_show_block_fault where municipality_id = :m")
                .param("m", a)
                .query(Integer.class)
                .single())
        .as("o bloqueo bórrase coas reservas que o causaron; as reservas quedan")
        .isZero();
    assertThat(
            jdbc.sql("select count(*) from booking where id in (:ids)")
                .param("ids", List.of(first, second))
                .query(Integer.class)
                .single())
        .isEqualTo(2);
  }
}
