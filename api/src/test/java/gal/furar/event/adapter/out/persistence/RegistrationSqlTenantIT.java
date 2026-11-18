// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.event.domain.EventId;
import gal.furar.event.domain.Participant;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link RegistrationSql} só le, bloquea, conta, cambia, borra e
 * anonimiza as inscricións e os eventos do seu concello.
 */
class RegistrationSqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

  @Autowired RegistrationSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  /** Un evento que empeza en {@code startsAt}, por JDBC. */
  UUID event(UUID municipality, Instant startsAt) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into event (id, municipality_id, name_gl, name_es, starts_at, ends_at, place,
                               published, approval, created_at, updated_at, version)
            values (:id, :m, 'Carreira', 'Carrera', :starts, :starts, 'Praza', true, false, :now,
                    :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("starts", Timestamp.from(startsAt))
        .param("now", Timestamp.from(NOW))
        .update();
    return id;
  }

  Registration registration(UUID event, String person, RegistrationStatus status) {
    return new Registration(
        RegistrationId.newId(),
        new EventId(event),
        status,
        new Participant(person, "Persoa", person + "@example.org", Language.GL),
        null,
        null,
        null,
        status == RegistrationStatus.CONFIRMED ? PaymentStatus.PENDING : null,
        null,
        null,
        null,
        NOW,
        NOW,
        0);
  }

  UUID registered(UUID municipality, UUID event, String person, RegistrationStatus status) {
    Registration r = registration(event, person, status);
    as(
        municipality,
        () -> {
          subject.insert(municipality, r);
          return null;
        });
    return r.id().value();
  }

  @Override
  protected Object seed(UUID municipality) {
    UUID event = event(municipality, NOW.plusSeconds(86400));
    return registered(municipality, event, "sub-" + municipality, RegistrationStatus.CONFIRMED);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.listForPerson(municipality, "sub-" + municipality).stream()
                .map(r -> (Object) r.id().value())
                .toList());
  }

  long rows(UUID municipality, String where) {
    return jdbc.sql(
            "select count(*) from event_registration where municipality_id = :m and " + where)
        .param("m", municipality)
        .query(Long.class)
        .single();
  }

  @Test
  void readsLocksCountsAndChangesOnlyItsOwn() {
    UUID a = id(createMunicipality(uniqueSlug("evr-a")));
    UUID b = id(createMunicipality(uniqueSlug("evr-b")));
    UUID eventA = event(a, NOW.plusSeconds(86400));
    UUID eventB = event(b, NOW.plusSeconds(86400));
    UUID mine = registered(a, eventA, "p1", RegistrationStatus.CONFIRMED);
    registered(a, eventA, "p2", RegistrationStatus.PENDING);
    UUID theirs = registered(b, eventB, "p1", RegistrationStatus.PENDING);

    assertThat(as(a, () -> subject.lockEvent(a, eventA))).isTrue();
    assertThat(as(a, () -> subject.lockEvent(a, eventB))).isFalse();
    assertThat(as(a, () -> subject.counts(a, List.of(eventA, eventB))))
        .containsExactly(new RegistrationSql.CountRow(eventA, 1, 1, 0, 0));
    assertThat(as(a, () -> subject.find(a, theirs))).isEmpty();
    assertThat(as(a, () -> subject.find(a, mine))).isPresent();
    assertThat(as(a, () -> subject.findByPerson(a, eventB, "p1"))).isEmpty();
    assertThat(as(a, () -> subject.listForEvent(a, eventB))).isEmpty();
    assertThat(as(a, () -> subject.listForPerson(a, "p1")))
        .extracting(r -> r.id().value())
        .containsExactly(mine);
    assertThat(as(a, () -> subject.countForEvent(a, eventB))).isZero();
    assertThat(as(a, () -> subject.countPendingFrom(a, NOW))).isEqualTo(1);

    Registration theirRow = registration(eventB, "p1", RegistrationStatus.CONFIRMED);
    Registration forged =
        new Registration(
            new RegistrationId(theirs),
            theirRow.eventId(),
            RegistrationStatus.CONFIRMED,
            theirRow.participant(),
            null,
            null,
            null,
            PaymentStatus.PAID,
            NOW,
            null,
            null,
            NOW,
            NOW,
            0);
    assertThat(as(a, () -> subject.update(a, forged))).isZero();
    assertThat(as(a, () -> subject.delete(a, theirs))).isZero();
    assertThat(rows(b, "status = 'PENDING'")).isEqualTo(1);
  }

  @Test
  void anonymizesOnlyItsOwnPastEvents() {
    UUID a = id(createMunicipality(uniqueSlug("evr-c")));
    UUID b = id(createMunicipality(uniqueSlug("evr-d")));
    UUID oldA = event(a, NOW.minusSeconds(3 * 365 * 86400L));
    UUID newA = event(a, NOW.plusSeconds(86400));
    UUID oldB = event(b, NOW.minusSeconds(3 * 365 * 86400L));
    registered(a, oldA, "p1", RegistrationStatus.CONFIRMED);
    registered(a, newA, "p1", RegistrationStatus.CONFIRMED);
    registered(b, oldB, "p1", RegistrationStatus.CONFIRMED);

    assertThat(as(a, () -> subject.anonymizeEndedBefore(a, NOW.minusSeconds(365 * 86400L), NOW)))
        .isEqualTo(1);
    assertThat(rows(a, "person_subject is null")).isEqualTo(1);
    assertThat(rows(b, "person_subject is null")).isZero();
  }

  @Test
  void theWaitlistReadsAndChangesOnlyItsOwn() {
    UUID a = id(createMunicipality(uniqueSlug("evr-e")));
    UUID b = id(createMunicipality(uniqueSlug("evr-f")));
    UUID eventA = event(a, NOW.plusSeconds(86400));
    UUID eventB = event(b, NOW.plusSeconds(86400));
    registered(a, eventA, "p1", RegistrationStatus.WAITING);
    UUID theirs = registered(b, eventB, "p1", RegistrationStatus.WAITING);

    assertThat(as(a, () -> subject.waitlist(a, eventB))).isEmpty();
    assertThat(as(a, () -> subject.waitlist(a, eventA))).hasSize(1);
    assertThat(as(a, () -> subject.listByAccount(a, eventB, "p1"))).isEmpty();
    assertThat(as(a, () -> subject.findByDependent(a, eventB, "p1", UUID.randomUUID()))).isEmpty();
    assertThat(as(a, () -> subject.countActiveWaits(a, "p1", NOW))).isEqualTo(1);
    assertThat(as(a, () -> subject.eventsWithWaiting(a, NOW))).containsExactly(eventA);
    assertThat(as(a, () -> subject.noticesToMail(a, NOW))).isEmpty();
    assertThat(as(a, () -> subject.markNoticeMailed(a, theirs, NOW))).isZero();
    // Cando o evento xa pasou, as esperas bórranse; as do outro concello, non.
    Instant later = NOW.plusSeconds(2 * 86400);
    assertThat(as(a, () -> subject.deleteClosedWaits(a, later, later))).isEqualTo(1);
    assertThat(rows(b, "status = 'WAITING'")).isEqualTo(1);
  }
}
