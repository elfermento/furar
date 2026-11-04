// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.port.out.BookingFilter;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): as lecturas de xestión de {@link BookingSearchSql} (lista, contas e calendario)
 * só ven as reservas do seu concello, coa RLS da transacción e co concello como parámetro.
 */
class BookingSearchSqlTenantIT extends AbstractTenantIT {

  static final Instant START = Instant.parse("2026-11-10T17:00:00Z");

  static final BookingFilter ALL =
      new BookingFilter(null, null, Set.of(), Set.of(), Set.of(), null, null, true);

  @Autowired BookingSearchSql subject;
  @Autowired TransactionTemplate tx;

  /** Unha reserva confirmada e pendente de pago de Ánxela Núñez, nunha instalación nova. */
  UUID insertBooking(UUID municipality, String code) {
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
               payment_status, price_kind, amount_cents, cancel_until, holder_subject,
               holder_name, holder_email, holder_language, created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED',
                    'PENDING', 'PAID', 800, :start, 'sub-ana', 'Ánxela Núñez',
                    'ana@example.org', 'gl', now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("code", code)
        .param("f", facility)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .update();
    return booking;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insertBooking(municipality, "FUR-AB23");
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                status ->
                    subject.recent(municipality, ALL, 100, 0).stream()
                        .map(b -> (Object) b.id().value())
                        .toList()));
  }

  /**
   * Co concello alleo como parámetro, ningunha lectura ve nada; co propio, cada filtro atopa a súa
   * reserva (tamén polo nome sen acentos e polo código).
   */
  @Test
  void everyReadSeesOnlyItsMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("search-a")));
    UUID b = id(createMunicipality(uniqueSlug("search-b")));
    UUID mine = insertBooking(a, "FUR-CD45");
    insertBooking(b, "FUR-CD45");
    BookingFilter filtered =
        new BookingFilter(
            START.minusSeconds(3600),
            START.plusSeconds(3600),
            Set.of(),
            Set.of(PaymentStatus.PENDING),
            Set.of(BookingStatus.CONFIRMED),
            "nunez",
            "FUR-CD45",
            false);
    Instant from = START.minusSeconds(86_400);
    Instant to = START.plusSeconds(86_400);

    List<Object> own = reads(a, a, filtered, from, to);
    List<Object> crossed = reads(a, b, filtered, from, to);

    assertThat(own).containsExactly(List.of(mine), List.of(mine), 1L, List.of(mine), 1, 1);
    assertThat(crossed).containsExactly(List.of(), List.of(), 0L, List.of(), 0, 0);
  }

  /** Cada lectura, como o concello {@code as}, pedindo co concello {@code param}. */
  List<Object> reads(UUID as, UUID param, BookingFilter filter, Instant from, Instant to) {
    return TenantContext.runAs(
        TenantRef.of(as),
        () ->
            tx.execute(
                status ->
                    List.<Object>of(
                        ids(subject.recent(param, filter, 10, 0)),
                        ids(subject.byStart(param, filter, 10, 0)),
                        subject.count(param, filter),
                        ids(subject.confirmedStarting(param, from, to, null)),
                        subject.perDay(param, from, to, null).size(),
                        subject.perHour(param, from, to, null).size())));
  }

  static List<UUID> ids(List<Booking> bookings) {
    return bookings.stream().map(b -> b.id().value()).toList();
  }

  @Test
  void escapesTheWildcards() {
    assertThat(BookingSearchSql.escaped("50%_!")).isEqualTo("50!%!_!!");
  }
}
