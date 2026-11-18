// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingWaitTallySql} só le e borra o reconto anónimo das
 * esperas do seu concello, coa RLS da transacción e co concello como parámetro.
 */
class BookingWaitTallySqlTenantIT extends AbstractTenantIT {

  static final Instant START = Instant.parse("2026-11-10T17:00:00Z");

  @Autowired BookingWaitTallySql subject;
  @Autowired TransactionTemplate tx;

  /** Os usos sementados: o que se lle pregunta á clase desde cada concello. */
  final List<UUID> seeded = new ArrayList<>();

  /** Un uso (coa súa instalación) e o reconto dunha franxa súa, gardado como dono da base. */
  UUID seedTally(UUID municipality, int waits) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility
              (id, municipality_id, name, street, postal_code, locality, latitude,
               longitude, created_at, updated_at, version)
            values (:id, :m, :name, 'Rúa do Alto', '15570', 'Narón', 43.5, -8.2,
                    now(), now(), 0)
            """)
        .param("id", facility)
        .param("m", municipality)
        .param("name", "Pista " + facility)
        .update();
    jdbc.sql(
            """
            insert into facility_use
              (id, municipality_id, facility_id, name, rental_mode, slot_minutes, availability,
               created_at, updated_at, version)
            values (:id, :m, :f, 'Pádel', 'WHOLE', 60, 'ALWAYS', now(), now(), 0)
            """)
        .param("id", use)
        .param("m", municipality)
        .param("f", facility)
        .update();
    jdbc.sql(
            """
            insert into booking_wait_tally (municipality_id, facility_id, use_id, slot_start, waits)
            values (:m, :f, :u, :start, :waits)
            """)
        .param("m", municipality)
        .param("f", facility)
        .param("u", use)
        .param("start", BookingSql.utc(START))
        .param("waits", waits)
        .update();
    return use;
  }

  <T> T as(UUID municipality, java.util.function.Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  @Override
  protected Object seed(UUID municipality) {
    UUID use = seedTally(municipality, 2);
    seeded.add(use);
    return use;
  }

  /** O uso de cada concello só ten reconto visto desde o seu concello. */
  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            seeded.stream()
                .filter(use -> subject.waits(municipality, use, START) > 0)
                .<Object>map(use -> use)
                .toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("tally-a")));
    UUID b = id(createMunicipality(uniqueSlug("tally-b")));
    UUID theirs = seedTally(b, 4);

    assertThat(as(a, () -> subject.waits(b, theirs, START))).isZero();
    assertThat(as(a, () -> subject.deleteBefore(b, START.plus(1, ChronoUnit.DAYS)))).isZero();
    assertThat(as(b, () -> subject.waits(b, theirs, START))).isEqualTo(4);
  }

  /** Sumar no concello alleo: a RLS ({@code with check}) non deixa. */
  @Test
  void itCannotWriteIntoAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("tally-wa")));
    UUID b = id(createMunicipality(uniqueSlug("tally-wb")));
    UUID theirs = seedTally(b, 1);
    UUID facility =
        jdbc.sql("select facility_id from facility_use where id = :u")
            .param("u", theirs)
            .query(UUID.class)
            .single();

    assertThatThrownBy(
            () ->
                as(
                    a,
                    () -> {
                      subject.add(b, facility, theirs, START.plusSeconds(3600), 1);
                      return null;
                    }))
        .isInstanceOf(DataAccessException.class);
    // Tampouco sobre a fila que xa existe.
    assertThatThrownBy(
            () ->
                as(
                    a,
                    () -> {
                      subject.add(b, facility, theirs, START, 5);
                      return null;
                    }))
        .isInstanceOf(DataAccessException.class);
    assertThat(as(b, () -> subject.waits(b, theirs, START))).isEqualTo(1);
  }

  @Test
  void addingCreatesTheRowAndThenSums() {
    UUID m = id(createMunicipality(uniqueSlug("tally-add")));
    UUID use = seedTally(m, 2);
    UUID facility =
        jdbc.sql("select facility_id from facility_use where id = :u")
            .param("u", use)
            .query(UUID.class)
            .single();
    Instant other = START.plusSeconds(3600);

    as(
        m,
        () -> {
          subject.add(m, facility, use, START, 3);
          subject.add(m, facility, use, other, 1);
          return null;
        });

    assertThat(as(m, () -> subject.waits(m, use, START))).isEqualTo(5);
    assertThat(as(m, () -> subject.waits(m, use, other))).isEqualTo(1);
  }

  @Test
  void itForgetsOnlyTheOldOnes() {
    UUID m = id(createMunicipality(uniqueSlug("tally-rw")));
    UUID use = seedTally(m, 3);

    assertThat(as(m, () -> subject.deleteBefore(m, START))).isZero();
    assertThat(as(m, () -> subject.waits(m, use, START))).isEqualTo(3);
    assertThat(as(m, () -> subject.deleteBefore(m, START.plusSeconds(1)))).isEqualTo(1);
    assertThat(as(m, () -> List.of(subject.waits(m, use, START)))).containsExactly(0);
  }
}
