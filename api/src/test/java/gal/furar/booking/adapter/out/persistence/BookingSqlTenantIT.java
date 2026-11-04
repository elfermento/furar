// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.CapPeriod;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.booking.domain.PriceKind;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingSql} só ve as reservas do seu concello, coa RLS da
 * transacción e co concello como parámetro. Tamén que le cada columna tal como se gardou.
 */
class BookingSqlTenantIT extends AbstractTenantIT {

  static final Instant START = Instant.parse("2026-11-10T17:00:00Z");

  @Autowired BookingSql subject;
  @Autowired TransactionTemplate tx;

  record Seeded(UUID facility, UUID use, UUID booking) {}

  Seeded insertBooking(UUID municipality, String code) {
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
               payment_status, price_kind, amount_cents, cancel_until, rule_max_slots,
               rule_cap_count, rule_cap_period, rule_cancel_hours, holder_subject, holder_name,
               holder_email, holder_language, created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '2 hours', '[)'), 2, 'CONFIRMED',
                    'PENDING', 'PAID', 1600, :start - interval '24 hours', 2, 3, 'WEEK', 24,
                    'sub-ana', 'Ana Rey', 'ana@example.org', 'es', now(), now(), 4)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("code", code)
        .param("f", facility)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .update();
    return new Seeded(facility, use, booking);
  }

  @Override
  protected Object seed(UUID municipality) {
    // O mesmo código nos dous concellos: só é único dentro de cada un.
    return insertBooking(municipality, "FUR-AB23").booking();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    List<UUID> all = jdbc.sql("select id from booking").query(UUID.class).list();
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                status -> {
                  List<Object> seen = new ArrayList<>();
                  for (UUID id : all) {
                    subject.find(municipality, id).ifPresent(b -> seen.add(b.id().value()));
                  }
                  return seen;
                }));
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("booking-a")));
    UUID b = id(createMunicipality(uniqueSlug("booking-b")));
    UUID theirs = insertBooking(b, "FUR-CD45").booking();
    List<Booking> seen =
        TenantContext.runAs(
            TenantRef.of(a), () -> tx.execute(status -> subject.find(b, theirs).stream().toList()));
    assertThat(seen).isEmpty();
  }

  @Test
  void activeOccupancyReadsOnlyItsMunicipalityAndTheActiveRowsThatOverlap() {
    UUID a = id(createMunicipality(uniqueSlug("occupancy-a")));
    UUID b = id(createMunicipality(uniqueSlug("occupancy-b")));
    Seeded mine = insertBooking(a, "FUR-EF67");
    Seeded theirs = insertBooking(b, "FUR-EF67");
    insertOccupancy(a, mine, true);
    insertOccupancy(b, theirs, true);
    Seeded cancelled = insertBooking(a, "FUR-GH89");
    insertOccupancy(a, cancelled, false);
    List<UUID> all = List.of(mine.facility(), theirs.facility(), cancelled.facility());

    List<BookingSql.FacilityOccupied> seen =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        subject.activeOccupancy(
                            a, all, START.minusSeconds(3600), START.plusSeconds(3600))));
    List<BookingSql.FacilityOccupied> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        subject.activeOccupancy(
                            b, all, START.minusSeconds(3600), START.plusSeconds(3600))));
    List<BookingSql.FacilityOccupied> before =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status -> subject.activeOccupancy(a, all, START.minusSeconds(3600), START)));

    assertThat(seen)
        .extracting(BookingSql.FacilityOccupied::facilityId)
        .containsExactly(mine.facility());
    assertThat(seen.getFirst().occupied().useId()).isEqualTo(mine.use());
    assertThat(seen.getFirst().occupied().start()).isEqualTo(START);
    assertThat(seen.getFirst().occupied().end()).isEqualTo(START.plusSeconds(7200));
    assertThat(crossed).isEmpty();
    assertThat(before).as("[from, to) que remata no inicio non a pisa").isEmpty();
  }

  void insertOccupancy(UUID municipality, Seeded seeded, boolean active) {
    jdbc.sql(
            """
            insert into booking_occupancy
              (id, municipality_id, booking_id, facility_id, use_id, rental_mode, period, active)
            values (:id, :m, :b, :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '2 hours', '[)'), :active)
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("b", seeded.booking())
        .param("f", seeded.facility())
        .param("u", seeded.use())
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .param("active", active)
        .update();
  }

  @Test
  void readsEveryColumnAsStored() {
    UUID municipality = id(createMunicipality(uniqueSlug("booking-read")));
    Seeded seeded = insertBooking(municipality, "FUR-XY78");
    Booking booking =
        TenantContext.runAs(
            TenantRef.of(municipality),
            () -> tx.execute(status -> subject.find(municipality, seeded.booking()).orElseThrow()));
    assertThat(booking.code().value()).isEqualTo("FUR-XY78");
    assertThat(booking.slot().facilityId()).isEqualTo(seeded.facility());
    assertThat(booking.slot().useId()).isEqualTo(seeded.use());
    assertThat(booking.slot().partId()).isNull();
    assertThat(booking.slot().rentalMode()).isEqualTo(RentalMode.WHOLE);
    assertThat(booking.slot().start()).isEqualTo(START);
    assertThat(booking.slot().end()).isEqualTo(START.plusSeconds(7200));
    assertThat(booking.slot().slots()).isEqualTo(2);
    assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(booking.price().kind()).isEqualTo(PriceKind.PAID);
    assertThat(booking.price().amountCents()).isEqualTo(1600);
    assertThat(booking.cancelUntil()).isEqualTo(START.minusSeconds(24 * 3600));
    assertThat(booking.rules().maxSlots()).isEqualTo(2);
    assertThat(booking.rules().capCount()).isEqualTo(3);
    assertThat(booking.rules().capPeriod()).isEqualTo(CapPeriod.WEEK);
    assertThat(booking.rules().cancelHours()).isEqualTo(24);
    assertThat(booking.rules().minAdvanceHours()).isNull();
    assertThat(booking.holder().subject()).isEqualTo("sub-ana");
    assertThat(booking.holder().language()).isEqualTo(Language.ES);
    assertThat(booking.cancelledAt()).isNull();
    assertThat(booking.version()).isEqualTo(4L);
  }

  /** As lecturas da persoa e as contas do límite tampouco cruzan o concello co parámetro. */
  @Test
  void theHolderQueriesSeeOnlyTheirMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("holder-a")));
    UUID b = id(createMunicipality(uniqueSlug("holder-b")));
    Seeded mine = insertBooking(a, "FUR-JK23");
    Seeded theirs = insertBooking(b, "FUR-JK23");
    Instant before = START.minusSeconds(3600);
    Instant after = START.plusSeconds(3 * 3600);

    List<Object> own =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        List.<Object>of(
                            subject.findOfHolder(a, mine.booking(), "sub-ana").isPresent(),
                            subject.upcoming(a, "sub-ana", before, 10, 0).size(),
                            subject.countUpcoming(a, "sub-ana", before),
                            subject.history(a, "sub-ana", after, 10, 0).size(),
                            subject.countHistory(a, "sub-ana", after),
                            subject.countConfirmed(a, mine.use(), "sub-ana", before, after, null),
                            subject.holds(a, mine.use(), "sub-ana", before, after, null))));
    List<Object> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        List.<Object>of(
                            subject.findOfHolder(b, theirs.booking(), "sub-ana").isPresent(),
                            subject.upcoming(b, "sub-ana", before, 10, 0).size(),
                            subject.countUpcoming(b, "sub-ana", before),
                            subject.history(b, "sub-ana", after, 10, 0).size(),
                            subject.countHistory(b, "sub-ana", after),
                            subject.countConfirmed(b, theirs.use(), "sub-ana", before, after, null),
                            subject.holds(b, theirs.use(), "sub-ana", before, after, null))));
    boolean otherPerson =
        TenantContext.runAs(
                TenantRef.of(a),
                () -> tx.execute(status -> subject.findOfHolder(a, mine.booking(), "sub-brais")))
            .isPresent();

    assertThat(own).containsExactly(true, 1, 1L, 1, 1L, 1, true);
    assertThat(crossed).containsExactly(false, 0, 0L, 0, 0L, 0, false);
    assertThat(otherPerson).isFalse();
  }

  /** As lecturas do modal de reservas afectadas e de «ten historial» tampouco cruzan o concello. */
  @Test
  void theImpactQueriesSeeOnlyTheirMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("impact-a")));
    UUID b = id(createMunicipality(uniqueSlug("impact-b")));
    Seeded mine = insertBooking(a, "FUR-NP67");
    Seeded theirs = insertBooking(b, "FUR-NP67");
    Instant now = START.minusSeconds(3600);
    Instant later = START.plusSeconds(3 * 3600);

    List<Object> own =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        List.<Object>of(
                            subject
                                .confirmedNotEnded(a, mine.facility(), null, now, now, later)
                                .size(),
                            subject
                                .confirmedNotEnded(
                                    a,
                                    mine.facility(),
                                    mine.use(),
                                    now,
                                    START,
                                    START.plusSeconds(1))
                                .size(),
                            subject
                                .confirmedNotEnded(
                                    a, mine.facility(), theirs.use(), now, now, later)
                                .size(),
                            subject
                                .confirmedNotEnded(
                                    a, mine.facility(), null, later, later, later.plusSeconds(1))
                                .size(),
                            subject.anyOf(a, mine.facility()))));
    List<Object> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        List.<Object>of(
                            subject
                                .confirmedNotEnded(b, theirs.facility(), null, now, now, later)
                                .size(),
                            subject.anyOf(b, theirs.facility()))));

    assertThat(own)
        .as("todas, por uso e período; outro uso e xa rematadas, non")
        .containsExactly(1, 1, 0, 0, true);
    assertThat(crossed).containsExactly(0, false);
  }

  /**
   * Cambiar e cancelar escriben só no seu concello: co concello alleo como parámetro non tocan
   * nada, e sen contar a reserva que se cambia o límite e a praza non a ven.
   */
  @Test
  void theWritesOfChangeAndCancelStayInTheirMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("write-a")));
    UUID b = id(createMunicipality(uniqueSlug("write-b")));
    Seeded theirs = insertBooking(b, "FUR-LM45");
    insertOccupancy(b, theirs, true);
    Booking stored =
        TenantContext.runAs(
            TenantRef.of(b),
            () -> tx.execute(status -> subject.find(b, theirs.booking()).orElseThrow()));
    Instant before = START.minusSeconds(3600);
    Instant after = START.plusSeconds(3 * 3600);

    List<Object> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    status ->
                        List.<Object>of(
                            subject.updateBooking(b, stored, 4),
                            subject.deactivateOccupancy(b, theirs.booking()),
                            subject.releaseSeat(b, theirs.use(), START),
                            subject.resizeSeats(b, theirs.use(), 5, START))));
    List<Object> own =
        TenantContext.runAs(
            TenantRef.of(b),
            () ->
                tx.execute(
                    status ->
                        List.<Object>of(
                            subject.countConfirmed(
                                b, theirs.use(), "sub-ana", before, after, theirs.booking()),
                            subject.holds(
                                b, theirs.use(), "sub-ana", before, after, theirs.booking()),
                            subject.updateBooking(b, stored, 3),
                            subject.updateBooking(b, stored, 4),
                            subject.deactivateOccupancy(b, theirs.booking()))));

    assertThat(crossed).containsExactly(false, 0, 0, 0);
    assertThat(own)
        .as("sen a que se cambia; versión vella → nada")
        .containsExactly(0, false, false, true, 1);
    assertThat(
            jdbc.sql("select version from booking where id = :id")
                .param("id", theirs.booking())
                .query(Long.class)
                .single())
        .isEqualTo(5L);
  }
}
