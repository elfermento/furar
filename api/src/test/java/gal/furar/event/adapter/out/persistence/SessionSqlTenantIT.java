// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSession;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.sql.Timestamp;
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
 * A8b (ADR-0006, ADR-0041): o SQL nativo de {@link SessionSql} só le, escribe, cancela e marca as
 * sesións e a asistencia do seu concello.
 */
class SessionSqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  static final LocalDate DAY = LocalDate.parse("2026-11-20");

  @Autowired SessionSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  /** Unha actividade noutro sitio, por JDBC. */
  UUID event(UUID municipality) {
    UUID id = UUID.randomUUID();
    Instant start = Instant.parse("2026-11-20T17:00:00Z");
    jdbc.sql(
            """
            insert into event (id, municipality_id, name_gl, name_es, kind, starts_at, ends_at,
                               place, weekdays, start_minute, end_minute, first_date, last_date,
                               published, approval, created_at, updated_at, version)
            values (:id, :m, 'Escola', 'Escuela', 'SCHEDULED', :starts, :ends, 'Parque', 16, 1080,
                    1140, :day, :day, true, false, :now, :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("starts", Timestamp.from(start))
        .param("ends", Timestamp.from(start.plusSeconds(3600)))
        .param("day", DAY)
        .param("now", Timestamp.from(NOW))
        .update();
    return id;
  }

  UUID registration(UUID municipality, UUID event) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into event_registration
              (id, municipality_id, event_id, status, person_subject, person_name, person_email,
               person_language, created_at, updated_at, version)
            values (:id, :m, :event, 'CONFIRMED', :sub, 'Persoa', 'p@example.org', 'gl', :now,
                    :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("event", event)
        .param("sub", "sub-" + id)
        .param("now", Timestamp.from(NOW))
        .update();
    return id;
  }

  UUID session(UUID municipality, UUID event) {
    EventSession s =
        new EventSession(
            UUID.randomUUID(),
            new EventId(event),
            DAY,
            Instant.parse("2026-11-20T17:00:00Z"),
            Instant.parse("2026-11-20T18:00:00Z"),
            null,
            null,
            null);
    as(
        municipality,
        () -> {
          subject.insertAll(municipality, List.of(s), NOW);
          return null;
        });
    return s.id();
  }

  @Override
  protected Object seed(UUID municipality) {
    return session(municipality, event(municipality));
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () -> subject.on(municipality, DAY).stream().map(t -> (Object) t.session().id()).toList());
  }

  long marks(UUID municipality) {
    return jdbc.sql("select count(*) from event_attendance where municipality_id = :m")
        .param("m", municipality)
        .query(Long.class)
        .single();
  }

  @Test
  void readsCancelsAndMarksOnlyItsOwn() {
    UUID a = id(createMunicipality(uniqueSlug("evs-a")));
    UUID b = id(createMunicipality(uniqueSlug("evs-b")));
    UUID eventA = event(a);
    UUID eventB = event(b);
    UUID mine = session(a, eventA);
    UUID theirs = session(b, eventB);
    UUID theirRegistration = registration(b, eventB);
    UUID myRegistration = registration(a, eventA);

    assertThat(as(a, () -> subject.listForEvent(a, eventB))).isEmpty();
    assertThat(as(a, () -> subject.listForEvent(a, eventA)))
        .extracting(EventSession::id)
        .containsExactly(mine);
    assertThat(as(a, () -> subject.find(a, eventB, theirs))).isEmpty();
    assertThat(as(a, () -> subject.byBookings(a, List.of(UUID.randomUUID())))).isEmpty();
    assertThat(as(a, () -> subject.cancel(a, List.of(theirs), NOW))).isZero();
    assertThat(as(a, () -> subject.updateMark(a, theirs, theirRegistration, true, NOW))).isZero();
    as(
        a,
        () -> {
          subject.insertMark(a, mine, myRegistration, false, NOW);
          subject.taken(a, mine, NOW, "Recepción");
          subject.deleteMark(a, theirs, theirRegistration);
          return null;
        });
    assertThat(as(a, () -> subject.marks(a, mine))).hasSize(1);
    assertThat(as(a, () -> subject.marks(a, theirs))).isEmpty();
    assertThat(as(a, () -> subject.saved(a, mine)))
        .hasValueSatisfying(s -> assertThat(s.byName()).isEqualTo("Recepción"));
    assertThat(as(a, () -> subject.saved(a, theirs))).isEmpty();
    assertThat(as(a, () -> subject.taken(a, theirs, NOW, "X"))).isZero();
    assertThat(as(a, () -> subject.tallies(a, eventA)))
        .containsExactly(new SessionSql.TallyRow(mine, 0, 1));
    assertThat(as(a, () -> subject.tallies(a, eventB))).isEmpty();
    assertThat(as(a, () -> subject.byRegistration(a, eventB))).isEmpty();
    assertThat(as(a, () -> subject.takenCount(a, eventA))).isEqualTo(1);
    assertThat(as(a, () -> subject.takenCount(a, eventB))).isZero();
    assertThat(as(a, () -> subject.cancel(a, List.of(mine), NOW))).isEqualTo(1);
    assertThat(marks(b)).isZero();
  }
}
