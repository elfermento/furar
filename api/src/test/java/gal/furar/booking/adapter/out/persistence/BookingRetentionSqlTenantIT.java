// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.port.out.BookingRetention;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingRetentionSql} só atopa e anonimiza as reservas do
 * seu concello, coa RLS da transacción e co concello como parámetro. Co concello alleo como
 * parámetro non ve nin cambia nada.
 */
class BookingRetentionSqlTenantIT extends AbstractTenantIT {

  static final String SUBJECT = "sub-conserva";
  static final String STAFF = "sub-persoal";
  static final Instant LONG_AGO = Instant.parse("2020-03-10T17:00:00Z");

  @Autowired BookingRetentionSql subject;
  @Autowired TransactionTemplate tx;

  /** Unha reserva rematada hai anos (coa súa instalación e uso), cancelada pola persoa. */
  UUID insertBooking(UUID municipality, String priceKind) {
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
    boolean paid = "PAID".equals(priceKind);
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, amount_cents, cancel_until, holder_subject,
               holder_name, holder_email, holder_language, cancelled_at, cancelled_by,
               created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CANCELLED_BY_PERSON',
                    :payment, :kind, :amount, :start, :subject, 'Ana Rey', 'ana@example.org',
                    'gl', :start, :subject, :start, :start, 0)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("code", paid ? "FUR-CD45" : "FUR-AB23")
        .param("f", facility)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(LONG_AGO, ZoneOffset.UTC))
        .param("payment", paid ? "TO_REFUND" : "NONE")
        .param("kind", priceKind)
        .param("amount", paid ? 800 : null, java.sql.Types.INTEGER)
        .param("subject", SUBJECT)
        .update();
    for (String actor : List.of(SUBJECT, STAFF)) {
      jdbc.sql(
              """
              insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
              values (:id, :m, :b, 'CREATED', :at, :actor)
              """)
          .param("id", UUID.randomUUID())
          .param("m", municipality)
          .param("b", booking)
          .param("at", OffsetDateTime.ofInstant(LONG_AGO, ZoneOffset.UTC))
          .param("actor", actor)
          .update();
    }
    return booking;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insertBooking(municipality, "NONE");
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                s ->
                    subject.expiredUnpaid(municipality, Instant.now(), 1000).stream()
                        .<Object>map(id -> id)
                        .toList()));
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("conserva-a")));
    UUID b = id(createMunicipality(uniqueSlug("conserva-b")));
    UUID theirs = insertBooking(b, "NONE");

    List<UUID> seen =
        TenantContext.runAs(
            TenantRef.of(a), () -> tx.execute(s -> subject.expiredUnpaid(b, Instant.now(), 10)));
    Integer changed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s ->
                        subject.forgetEventActors(b, List.of(theirs))
                            + subject.forgetCanceller(b, List.of(theirs))
                            + subject.forgetHolder(b, List.of(theirs), Instant.now())));

    assertThat(seen).isEmpty();
    assertThat(changed).isZero();
    assertThat(holderOf(theirs)).isEqualTo(SUBJECT);
  }

  @Test
  void itAnonymisesThePersonButKeepsTheStaffInTheHistory() {
    UUID m = id(createMunicipality(uniqueSlug("conserva-rw")));
    UUID free = insertBooking(m, "NONE");
    UUID paid = insertBooking(m, "PAID");

    List<UUID> unpaid =
        TenantContext.runAs(
            TenantRef.of(m), () -> tx.execute(s -> subject.expiredUnpaid(m, Instant.now(), 10)));
    List<UUID> priced =
        TenantContext.runAs(
            TenantRef.of(m), () -> tx.execute(s -> subject.expiredPaid(m, Instant.now(), 10)));
    List<UUID> notYet =
        TenantContext.runAs(
            TenantRef.of(m), () -> tx.execute(s -> subject.expiredUnpaid(m, LONG_AGO, 10)));
    Integer anonymised =
        TenantContext.runAs(
            TenantRef.of(m),
            () ->
                tx.execute(
                    s -> {
                      subject.forgetEventActors(m, List.of(free));
                      subject.forgetCanceller(m, List.of(free));
                      return subject.forgetHolder(m, List.of(free), Instant.now());
                    }));
    Integer again =
        TenantContext.runAs(
            TenantRef.of(m),
            () -> tx.execute(s -> subject.forgetHolder(m, List.of(free), Instant.now())));

    assertThat(unpaid).containsExactly(free);
    assertThat(priced).containsExactly(paid);
    assertThat(notYet).isEmpty();
    assertThat(anonymised).isEqualTo(1);
    assertThat(again).isZero();
    assertThat(holderOf(free)).isNull();
    assertThat(holderOf(paid)).isEqualTo(SUBJECT);
    assertThat(
            jdbc.sql("select cancelled_by from booking where id = :id")
                .param("id", free)
                .query(String.class)
                .single())
        .isEqualTo(BookingRetention.ANONYMISED_ACTOR);
    assertThat(
            jdbc.sql(
                    "select actor_subject from booking_event where booking_id = :id"
                        + " order by actor_subject")
                .param("id", free)
                .query(String.class)
                .list())
        .containsExactly(BookingRetention.ANONYMISED_ACTOR, STAFF);
  }

  String holderOf(UUID booking) {
    return jdbc.sql("select holder_subject from booking where id = :id")
        .param("id", booking)
        .query(String.class)
        .list()
        .getFirst();
  }
}
