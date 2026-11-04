// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.application.port.out.IdempotencyKeys;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link IdempotencyKeySql} só le, garda e borra as claves de
 * reintento do seu concello, coa RLS da transacción e co concello como parámetro. A mesma persoa e
 * a mesma clave nos dous concellos son dúas filas distintas.
 */
class IdempotencyKeySqlTenantIT extends AbstractTenantIT {

  static final String SUBJECT = "sub-ana";
  static final String KEY = "retry-intent-0001";
  static final String FINGERPRINT = "a".repeat(64);
  static final Instant START = Instant.parse("2026-11-10T17:00:00Z");

  @Autowired IdempotencyKeySql subject;
  @Autowired TransactionTemplate tx;

  /** Unha reserva (coa súa instalación e uso), sen clave. */
  UUID insertBooking(UUID municipality) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
    UUID booking = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility
              (id, municipality_id, name, street, postal_code, locality, ine_code, latitude,
               longitude, created_at, updated_at, version)
            values (:id, :m, :name, 'Rúa do Alto', '15570', 'Narón', '15054', 43.5, -8.2,
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
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_language, created_at, updated_at, version)
            values (:id, :m, 'FUR-AB23', :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED', 'NONE',
                    'NONE', :start, :subject, 'Ana Rey', 'ana@example.org', 'gl', now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("f", facility)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .param("subject", SUBJECT)
        .update();
    return booking;
  }

  void insertKey(UUID municipality, UUID booking, Instant at) {
    jdbc.sql(
            """
            insert into booking_idempotency_key
              (id, municipality_id, holder_subject, idempotency_key, fingerprint, booking_id,
               created_at)
            values (:id, :m, :s, :k, :fp, :b, :at)
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("s", SUBJECT)
        .param("k", KEY)
        .param("fp", FINGERPRINT)
        .param("b", booking)
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .update();
  }

  @Override
  protected Object seed(UUID municipality) {
    // A mesma persoa e a mesma clave en cada concello.
    UUID booking = insertBooking(municipality);
    insertKey(municipality, booking, Instant.now());
    return booking;
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                s ->
                    subject.find(municipality, SUBJECT, KEY).stream()
                        .<Object>map(r -> r.bookingId().value())
                        .toList()));
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("idem-a")));
    UUID b = id(createMunicipality(uniqueSlug("idem-b")));
    insertKey(b, insertBooking(b), Instant.now());

    List<IdempotencyKeys.Remembered> seen =
        TenantContext.runAs(
            TenantRef.of(a),
            () -> tx.execute(s -> subject.find(b, SUBJECT, KEY).stream().toList()));
    Integer deleted =
        TenantContext.runAs(
            TenantRef.of(a),
            () -> tx.execute(s -> subject.deleteCreatedBefore(b, Instant.now().plusSeconds(60))));

    assertThat(seen).isEmpty();
    assertThat(deleted).isZero();
    assertThat(
            jdbc.sql("select count(*) from booking_idempotency_key where municipality_id = :m")
                .param("m", b)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  /** Gardar no concello alleo: a RLS ({@code with check}) non deixa. */
  @Test
  void itCannotWriteIntoAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("idem-wa")));
    UUID b = id(createMunicipality(uniqueSlug("idem-wb")));
    UUID theirs = insertBooking(b);

    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    TenantRef.of(a),
                    () ->
                        tx.execute(
                            s -> {
                              subject.insert(b, SUBJECT, KEY, FINGERPRINT, theirs, Instant.now());
                              return null;
                            })))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void itSavesReadsAndForgetsOnlyTheOldOnes() {
    UUID m = id(createMunicipality(uniqueSlug("idem-rw")));
    UUID booking = insertBooking(m);
    Instant now = Instant.now();

    IdempotencyKeys.Remembered saved =
        TenantContext.runAs(
            TenantRef.of(m),
            () ->
                tx.execute(
                    s -> {
                      subject.insert(m, SUBJECT, KEY, FINGERPRINT, booking, now);
                      return subject.find(m, SUBJECT, KEY).orElseThrow();
                    }));
    Integer notYet =
        TenantContext.runAs(
            TenantRef.of(m), () -> tx.execute(s -> subject.deleteCreatedBefore(m, now)));
    Integer old =
        TenantContext.runAs(
            TenantRef.of(m),
            () -> tx.execute(s -> subject.deleteCreatedBefore(m, now.plusSeconds(1))));

    assertThat(saved.fingerprint()).isEqualTo(FINGERPRINT);
    assertThat(saved.bookingId().value()).isEqualTo(booking);
    assertThat(notYet).isZero();
    assertThat(old).isEqualTo(1);
  }
}
