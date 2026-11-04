// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.StatsUnit;
import gal.furar.booking.application.port.out.StatsWindow;
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
 * A8b (ADR-0006): os agregados de {@link BookingStatsSql} só contan as reservas do seu concello,
 * coa RLS da transacción e co concello como parámetro.
 */
class BookingStatsSqlTenantIT extends AbstractTenantIT {

  static final Instant START = Instant.parse("2026-11-10T17:00:00Z");

  static final StatsWindow WINDOW =
      new StatsWindow(START.minusSeconds(86_400), START.plusSeconds(86_400), null, Set.of());

  @Autowired BookingStatsSql subject;
  @Autowired TransactionTemplate tx;

  /**
   * Unha reserva pagada e outra cancelada polo concello, nunha instalación nova; devolve a
   * instalación.
   */
  UUID insertBookings(UUID municipality) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
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
               holder_name, holder_email, holder_language, cancelled_at, cancelled_by,
               created_at, updated_at, version)
            values (:id, :m, 'FUR-AB23', :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED',
                    'PAID', 'PAID', 800, :start, 'sub-ana', 'Ánxela Núñez',
                    'ana@example.org', 'gl', null, null, now(), now(), 0),
                   (:other, :m, 'FUR-CD45', :f, :u, 'WHOLE',
                    tstzrange(:start + interval '1 hour', :start + interval '2 hour', '[)'), 1,
                    'CANCELLED_BY_MUNICIPALITY', 'NONE', 'FREE', null, :start, 'sub-ana',
                    'Ánxela Núñez', 'ana@example.org', 'gl', :start, 'sub-desk', now(), now(), 0)
            """)
        .param("id", UUID.randomUUID())
        .param("other", UUID.randomUUID())
        .param("m", municipality)
        .param("f", facility)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .update();
    return facility;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insertBookings(municipality);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                status ->
                    subject.perUseAndDay(municipality, WINDOW).stream()
                        .map(d -> (Object) d.facilityId())
                        .toList()));
  }

  /** Co concello alleo como parámetro, ningunha lectura conta nada; co propio, todas contan. */
  @Test
  void everyReadSeesOnlyItsMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("stats-a")));
    UUID b = id(createMunicipality(uniqueSlug("stats-b")));
    insertBookings(a);
    insertBookings(b);

    assertThat(reads(a, a)).containsExactly(1, 1, 1, 1, 1L, 1, 1, 1);
    assertThat(reads(a, b)).containsExactly(0, 0, 0, 0, 0L, 0, 0, 0);
  }

  /**
   * Cantas filas (ou persoas) dá cada lectura, como o concello {@code as}, pedindo con {@code
   * param}.
   */
  List<Object> reads(UUID as, UUID param) {
    return TenantContext.runAs(
        TenantRef.of(as),
        () ->
            tx.execute(
                status ->
                    List.<Object>of(
                        subject.perUseAndDay(param, WINDOW).size(),
                        subject.perUseWeekdayHour(param, WINDOW).size(),
                        subject.perBucket(param, WINDOW, StatsUnit.WEEK).size(),
                        subject.peoplePerBucket(param, WINDOW, StatsUnit.MONTH).size(),
                        subject.people(param, WINDOW),
                        subject.cancellations(param, WINDOW, StatsUnit.DAY).size(),
                        subject.money(param, WINDOW).size(),
                        subject.useIds(param, WINDOW).size())));
  }
}
