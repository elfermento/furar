// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.domain.Wait;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingWaitSql} só le, garda, borra e vence as esperas do
 * seu concello, coa RLS da transacción e co concello como parámetro. A mesma conta esperando en
 * dous concellos son dúas filas distintas, cada unha co seu límite.
 */
class BookingWaitSqlTenantIT extends AbstractTenantIT {

  static final String SUBJECT = "sub-ana";
  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
  static final Instant START = NOW.plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

  @Autowired BookingWaitSql subject;
  @Autowired TransactionTemplate tx;

  /** Un uso (coa súa instalación) no concello. */
  UUID[] insertUse(UUID municipality) {
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
    return new UUID[] {facility, use};
  }

  static Wait wait(UUID[] use, String account, Instant start) {
    return new Wait(
        UUID.randomUUID(),
        use[0],
        use[1],
        start,
        start.plus(1, ChronoUnit.HOURS),
        account,
        "ana@example.org",
        Language.GL,
        NOW);
  }

  /** Garda a espera como dono da base (sen RLS), para sementar. */
  UUID seedWait(UUID municipality, UUID[] use, Instant start, int ordinal) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into booking_wait
              (id, municipality_id, facility_id, use_id, slot_start, slot_end, account_subject,
               email_key, language, ordinal, created_at)
            values (:id, :m, :f, :u, :start, :start + interval '1 hour', :s, 'ana@example.org',
                    'gl', :o, now())
            """)
        .param("id", id)
        .param("m", municipality)
        .param("f", use[0])
        .param("u", use[1])
        .param("start", BookingSql.utc(start))
        .param("s", SUBJECT)
        .param("o", ordinal)
        .update();
    return id;
  }

  <T> T as(UUID municipality, java.util.function.Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  int waitsOf(UUID municipality) {
    return jdbc.sql("select count(*) from booking_wait where municipality_id = :m")
        .param("m", municipality)
        .query(Integer.class)
        .single();
  }

  @Override
  protected Object seed(UUID municipality) {
    // A mesma conta esperando en cada concello.
    return seedWait(municipality, insertUse(municipality), START, 1);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.activeOfAccount(municipality, SUBJECT, NOW).stream()
                .<Object>map(Wait::id)
                .toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("wait-a")));
    UUID b = id(createMunicipality(uniqueSlug("wait-b")));
    UUID[] use = insertUse(b);
    UUID theirs = seedWait(b, use, START, 1);
    seedWait(b, use, NOW.minus(1, ChronoUnit.HOURS), 2);

    assertThat(as(a, () -> subject.activeOfAccount(b, SUBJECT, NOW))).isEmpty();
    assertThat(as(a, () -> subject.findOfAccount(b, SUBJECT, use[1], START, NOW))).isEmpty();
    assertThat(
            as(
                a,
                () -> subject.activeOfFacility(b, use[0], NOW, NOW.plus(10, ChronoUnit.DAYS), NOW)))
        .isEmpty();
    assertThat(as(a, () -> subject.ofSlot(b, use[1], START, NOW, NOW))).isEmpty();
    assertThat(as(a, () -> subject.delete(b, theirs, SUBJECT))).isZero();
    assertThat(as(a, () -> subject.deleteAll(b, List.of(theirs)))).isZero();
    assertThat(as(a, () -> subject.refreshEmail(b, SUBJECT, "outra@example.org"))).isZero();
    assertThat(
            as(
                a,
                () ->
                    subject.deleteServed(
                        b, "ana@example.org", use[1], START, START.plusSeconds(3600))))
        .isZero();
    assertThat(as(a, () -> subject.deletePast(b, NOW))).isEmpty();
    assertThat(as(a, () -> subject.deletePastOfAccount(b, SUBJECT, NOW))).isEmpty();
    assertThat(waitsOf(b)).isEqualTo(2);
  }

  /** Gardar no concello alleo: a RLS ({@code with check}) non deixa. */
  @Test
  void itCannotWriteIntoAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("wait-wa")));
    UUID b = id(createMunicipality(uniqueSlug("wait-wb")));
    UUID[] theirs = insertUse(b);

    assertThatThrownBy(() -> as(a, () -> subject.insert(b, wait(theirs, SUBJECT, START), 1)))
        .isInstanceOf(DataAccessException.class);
  }

  /** O ordinal repetido e a franxa repetida non entran, e a transacción segue viva. */
  @Test
  void aTakenOrdinalOrSlotDoesNotEnterAndDoesNotAbort() {
    UUID m = id(createMunicipality(uniqueSlug("wait-rw")));
    UUID[] use = insertUse(m);

    List<Boolean> entered =
        as(
            m,
            () ->
                List.of(
                    subject.insert(m, wait(use, SUBJECT, START), 1),
                    subject.insert(m, wait(use, SUBJECT, START.plusSeconds(3600)), 1),
                    subject.insert(m, wait(use, SUBJECT, START), 2),
                    subject.insert(m, wait(use, SUBJECT, START.plusSeconds(3600)), 2),
                    subject.insert(m, wait(use, "sub-outra", START), 1)));

    assertThat(entered).containsExactly(true, false, false, true, true);
    assertThat(waitsOf(m)).isEqualTo(3);
  }

  /** Vencer borra as pasadas e di de que uso e franxa eran; as activas quedan. */
  @Test
  void expiringDeletesThePastOnesOnce() {
    UUID m = id(createMunicipality(uniqueSlug("wait-ex")));
    UUID[] use = insertUse(m);
    Instant past = NOW.minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
    seedWait(m, use, past, 1);
    seedWait(m, use, START, 2);

    List<BookingWaitSql.Gone> gone = as(m, () -> subject.deletePast(m, NOW));

    assertThat(gone).containsExactly(new BookingWaitSql.Gone(use[0], use[1], past));
    assertThat(as(m, () -> subject.deletePast(m, NOW))).isEmpty();
    assertThat(waitsOf(m)).isEqualTo(1);
  }
}
