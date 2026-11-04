// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.BookingStatistics.CancellationBucket;
import gal.furar.booking.BookingStatistics.FacilityMoney;
import gal.furar.booking.BookingStatistics.PeopleBucket;
import gal.furar.booking.BookingStatistics.UseBucket;
import gal.furar.booking.BookingStatistics.UseDay;
import gal.furar.booking.BookingStatistics.UseWeekdayHour;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link BookingStatistics} contra PostgreSQL real (plan do dominio 3, §1.2.3 e §1.2.4): o día e a
 * hora en Europe/Madrid tamén nos cambios de hora, a reserva partida por horas, as canceladas fóra
 * das contas, as tres clases de cancelación, os catro cobros, as persoas distintas, os filtros e a
 * semana de luns. Cada proba ten o seu concello e as reservas van por JDBC.
 */
class BookingStatisticsIT extends AbstractIT {

  static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

  @Autowired BookingStatistics statistics;

  UUID municipality;
  UUID pitch;
  UUID court;
  UUID football;
  UUID futsal;
  UUID padel;
  final AtomicInteger codes = new AtomicInteger();

  /** O campo (fútbol e fútbol sala) e a pista (pádel), nun concello novo. */
  @BeforeEach
  void facilities() {
    municipality = id(createMunicipality(uniqueSlug("stats")));
    pitch = facility("Campo da Gándara");
    court = facility("Pista do Alto");
    football = use(pitch, "Fútbol 11");
    futsal = use(pitch, "Fútbol sala");
    padel = use(court, "Pádel");
  }

  @Test
  void countsTheDayInMadridOnTheSpringChange() {
    confirmed(football, local("2026-03-28T23:30"), 60, "sub-a");
    confirmed(football, local("2026-03-29T00:30"), 60, "sub-a");
    // 01:30 CET → 03:30 CEST: unha hora real, partida entre as 01 e as 03 locais.
    confirmed(futsal, Instant.parse("2026-03-29T00:30:00Z"), 60, "sub-b");
    // Pasa da medianoite: a hora 0 é do luns, pero a reserva conta no domingo.
    confirmed(football, local("2026-03-29T23:30"), 60, "sub-a");
    confirmed(football, local("2026-03-30T00:30"), 60, "sub-a");
    StatsQuery day = query("2026-03-29", "2026-03-29");

    assertThat(as(() -> statistics.perUseAndDay(day)))
        .containsExactlyInAnyOrder(
            new UseDay(football, pitch, LocalDate.parse("2026-03-29"), 2, 120),
            new UseDay(futsal, pitch, LocalDate.parse("2026-03-29"), 1, 60));
    assertThat(as(() -> statistics.perUseWeekdayHour(day)))
        .containsExactlyInAnyOrder(
            new UseWeekdayHour(football, 7, 0, 30),
            new UseWeekdayHour(football, 7, 1, 30),
            new UseWeekdayHour(football, 7, 23, 30),
            new UseWeekdayHour(football, 1, 0, 30),
            new UseWeekdayHour(futsal, 7, 1, 30),
            new UseWeekdayHour(futsal, 7, 3, 30));
  }

  @Test
  void countsTheDayInMadridOnTheAutumnChange() {
    confirmed(padel, local("2026-10-24T23:30"), 60, "sub-a");
    // 02:30 CEST → 02:30 CET: unha hora real, toda na hora local 2 (que ese día dura dúas).
    confirmed(padel, Instant.parse("2026-10-25T00:30:00Z"), 60, "sub-a");
    confirmed(padel, local("2026-10-25T23:00"), 60, "sub-a");
    confirmed(padel, local("2026-10-26T00:00"), 60, "sub-a");
    StatsQuery day = query("2026-10-25", "2026-10-25");

    assertThat(as(() -> statistics.perUseAndDay(day)))
        .containsExactly(new UseDay(padel, court, LocalDate.parse("2026-10-25"), 2, 120));
    assertThat(as(() -> statistics.perUseWeekdayHour(day)))
        .containsExactly(new UseWeekdayHour(padel, 7, 2, 60), new UseWeekdayHour(padel, 7, 23, 60));
    assertThat(as(() -> statistics.perBucket(day, StatsUnit.DAY)))
        .containsExactly(new UseBucket(LocalDate.parse("2026-10-25"), padel, 2, 120, 0));
  }

  @Test
  void splitsABookingByTheHoursItTouches() {
    confirmed(padel, local("2026-11-09T10:00"), 90, "sub-a");
    confirmed(padel, local("2026-11-09T12:15"), 30, "sub-b");

    assertThat(as(() -> statistics.perUseWeekdayHour(query("2026-11-09", "2026-11-09"))))
        .containsExactly(
            new UseWeekdayHour(padel, 1, 10, 60),
            new UseWeekdayHour(padel, 1, 11, 30),
            new UseWeekdayHour(padel, 1, 12, 30));
  }

  @Test
  void leavesCancelledBookingsOutOfTheCounts() {
    confirmed(padel, local("2026-11-10T18:00"), 60, "sub-a");
    cancelledByPerson(padel, local("2026-11-10T19:00"), "sub-b", true);
    cancelledByMunicipality(padel, local("2026-11-10T20:00"), "sub-c");
    StatsQuery q = query("2026-11-10", "2026-11-10");

    assertThat(as(() -> statistics.perUseAndDay(q)))
        .containsExactly(new UseDay(padel, court, LocalDate.parse("2026-11-10"), 1, 60));
    assertThat(as(() -> statistics.perUseWeekdayHour(q)))
        .containsExactly(new UseWeekdayHour(padel, 2, 18, 60));
    assertThat(as(() -> statistics.perBucket(q, StatsUnit.MONTH)))
        .containsExactly(new UseBucket(LocalDate.parse("2026-11-01"), padel, 1, 60, 0));
    assertThat(as(() -> statistics.people(q))).isEqualTo(1);
    assertThat(as(() -> statistics.useIds(q))).containsExactly(padel);
  }

  @Test
  void splitsCancellationsInTimeLateAndByTheMunicipality() {
    cancelledByPerson(padel, local("2026-11-10T19:00"), "sub-a", true);
    cancelledByPerson(padel, local("2026-11-11T19:00"), "sub-b", true);
    cancelledByPerson(padel, local("2026-11-12T19:00"), "sub-c", false);
    cancelledByMunicipality(football, local("2026-11-13T19:00"), "sub-d");
    cancelledByMunicipality(football, local("2026-11-16T19:00"), "sub-d");
    confirmed(padel, local("2026-11-12T20:00"), 60, "sub-e");

    assertThat(
            as(() -> statistics.cancellations(query("2026-11-09", "2026-11-22"), StatsUnit.WEEK)))
        .containsExactly(
            new CancellationBucket(LocalDate.parse("2026-11-09"), 2, 1, 1),
            new CancellationBucket(LocalDate.parse("2026-11-16"), 0, 0, 1));
  }

  @Test
  void addsTheFourKindsOfPayment() {
    paid(padel, local("2026-11-10T10:00"), "CONFIRMED", "PAID", 800);
    paid(padel, local("2026-11-10T11:00"), "CONFIRMED", "PENDING", 500);
    paid(padel, local("2026-11-10T12:00"), "CANCELLED_BY_PERSON", "PENDING", 300);
    paid(padel, local("2026-11-10T13:00"), "CANCELLED_BY_MUNICIPALITY", "TO_REFUND", 700);
    paid(padel, local("2026-11-10T14:00"), "CANCELLED_BY_PERSON", "REFUNDED", 900);
    paid(football, local("2026-11-11T10:00"), "CONFIRMED", "PAID", 4000);
    confirmed(football, local("2026-11-11T12:00"), 60, "sub-free");
    StatsQuery q = query("2026-11-01", "2026-11-30");

    assertThat(as(() -> statistics.money(q)))
        .containsExactlyInAnyOrder(
            new FacilityMoney(court, 800, 500, 700, 900), new FacilityMoney(pitch, 4000, 0, 0, 0));
    assertThat(as(() -> statistics.perBucket(q, StatsUnit.MONTH)))
        .containsExactlyInAnyOrder(
            new UseBucket(LocalDate.parse("2026-11-01"), padel, 2, 120, 800),
            new UseBucket(LocalDate.parse("2026-11-01"), football, 2, 120, 4000));
  }

  @Test
  void countsDistinctPeopleOverThePeriodNotTheSumOfMonths() {
    confirmed(padel, local("2026-10-05T10:00"), 60, "sub-a");
    confirmed(padel, local("2026-10-06T10:00"), 60, "sub-a");
    confirmed(padel, local("2026-11-05T10:00"), 60, "sub-a");
    confirmed(padel, local("2026-11-06T10:00"), 60, "sub-b");
    cancelledByPerson(padel, local("2026-11-07T10:00"), "sub-c", true);
    StatsQuery q = query("2026-10-01", "2026-11-30");

    List<PeopleBucket> months = as(() -> statistics.peoplePerBucket(q, StatsUnit.MONTH));
    assertThat(months)
        .containsExactly(
            new PeopleBucket(LocalDate.parse("2026-10-01"), 1),
            new PeopleBucket(LocalDate.parse("2026-11-01"), 2));
    assertThat(as(() -> statistics.people(q)))
        .isEqualTo(2)
        .isNotEqualTo(months.stream().mapToLong(PeopleBucket::people).sum());
    assertThat(as(() -> statistics.peoplePerBucket(q, StatsUnit.QUARTER)))
        .containsExactly(new PeopleBucket(LocalDate.parse("2026-10-01"), 2));
    assertThat(as(() -> statistics.peoplePerBucket(q, StatsUnit.YEAR)))
        .containsExactly(new PeopleBucket(LocalDate.parse("2026-01-01"), 2));
  }

  @Test
  void filtersByFacilityAndSeveralUses() {
    confirmed(football, local("2026-11-10T10:00"), 60, "sub-a");
    confirmed(futsal, local("2026-11-10T12:00"), 60, "sub-b");
    confirmed(padel, local("2026-11-10T10:00"), 60, "sub-c");
    cancelledByMunicipality(padel, local("2026-11-10T11:00"), "sub-d");
    LocalDate from = LocalDate.parse("2026-11-10");

    StatsQuery all = new StatsQuery(from, from, null, Set.of());
    StatsQuery onPitch = new StatsQuery(from, from, pitch, Set.of());
    StatsQuery twoUses = new StatsQuery(from, from, null, Set.of(football, padel));
    StatsQuery mismatch = new StatsQuery(from, from, court, Set.of(football));

    assertThat(as(() -> statistics.useIds(all))).containsExactlyInAnyOrder(football, futsal, padel);
    assertThat(as(() -> statistics.useIds(onPitch))).containsExactlyInAnyOrder(football, futsal);
    assertThat(as(() -> statistics.useIds(twoUses))).containsExactlyInAnyOrder(football, padel);
    assertThat(as(() -> statistics.useIds(mismatch))).isEmpty();
    assertThat(as(() -> statistics.people(onPitch))).isEqualTo(2);
    assertThat(as(() -> statistics.people(twoUses))).isEqualTo(2);
    assertThat(as(() -> statistics.perUseAndDay(twoUses)))
        .extracting(UseDay::useId)
        .containsExactlyInAnyOrder(football, padel);
    assertThat(as(() -> statistics.cancellations(onPitch, StatsUnit.DAY))).isEmpty();
    assertThat(as(() -> statistics.cancellations(twoUses, StatsUnit.DAY)))
        .containsExactly(new CancellationBucket(from, 0, 0, 1));
  }

  @Test
  void startsTheWeekOnMonday() {
    confirmed(padel, local("2026-11-08T10:00"), 60, "sub-a");
    confirmed(padel, local("2026-11-09T10:00"), 60, "sub-a");
    confirmed(padel, local("2026-11-15T23:30"), 30, "sub-a");

    assertThat(as(() -> statistics.perBucket(query("2026-11-02", "2026-11-15"), StatsUnit.WEEK)))
        .containsExactly(
            new UseBucket(LocalDate.parse("2026-11-02"), padel, 1, 60, 0),
            new UseBucket(LocalDate.parse("2026-11-09"), padel, 2, 90, 0));
  }

  @Test
  void returnsNothingWithoutBookings() {
    StatsQuery q = query("2026-11-01", "2026-11-30");

    assertThat(as(() -> statistics.people(q))).isZero();
    assertThat(as(() -> statistics.perBucket(q, StatsUnit.DAY))).isEmpty();
    assertThat(as(() -> statistics.money(q))).isEmpty();
  }

  <T> T as(Supplier<T> read) {
    return TenantContext.runAs(TenantRef.of(municipality), read);
  }

  static StatsQuery query(String from, String to) {
    return new StatsQuery(LocalDate.parse(from), LocalDate.parse(to), null, Set.of());
  }

  static Instant local(String at) {
    return LocalDateTime.parse(at).atZone(MADRID).toInstant();
  }

  UUID facility(String name) {
    UUID facility = UUID.randomUUID();
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
        .param("name", name)
        .update();
    return facility;
  }

  UUID use(UUID facility, String name) {
    UUID use = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility_use
              (id, municipality_id, facility_id, name, rental_mode, slot_minutes, availability,
               created_at, updated_at, version)
            values (:id, :m, :f, :name, 'WHOLE', 60, 'ALWAYS', now(), now(), 0)
            """)
        .param("id", use)
        .param("m", municipality)
        .param("f", facility)
        .param("name", name)
        .update();
    return use;
  }

  UUID confirmed(UUID use, Instant start, int minutes, String subject) {
    return insert(use, start, minutes, subject, "CONFIRMED", "NONE", null, null);
  }

  /** Cancelada pola persoa: en prazo, un día antes do límite; fóra, xusto no límite. */
  UUID cancelledByPerson(UUID use, Instant start, String subject, boolean inTime) {
    Instant cancelUntil = start.minus(Duration.ofHours(24));
    Instant cancelledAt = inTime ? cancelUntil.minus(Duration.ofDays(1)) : cancelUntil;
    return insert(use, start, 60, subject, "CANCELLED_BY_PERSON", "NONE", null, cancelledAt);
  }

  UUID cancelledByMunicipality(UUID use, Instant start, String subject) {
    return insert(
        use, start, 60, subject, "CANCELLED_BY_MUNICIPALITY", "NONE", null, start.minusSeconds(60));
  }

  UUID paid(UUID use, Instant start, String status, String payment, int cents) {
    Instant cancelledAt = status.equals("CONFIRMED") ? null : start.minus(Duration.ofDays(2));
    return insert(use, start, 60, "sub-" + payment, status, payment, cents, cancelledAt);
  }

  UUID insert(
      UUID use,
      Instant start,
      int minutes,
      String subject,
      String status,
      String payment,
      @Nullable Integer cents,
      @Nullable Instant cancelledAt) {
    UUID facility = use.equals(padel) ? court : pitch;
    UUID booking = UUID.randomUUID();
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, amount_cents, cancel_until, holder_subject,
               holder_name, holder_email, holder_language, cancelled_at, cancelled_by,
               created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'WHOLE', tstzrange(:start, :end, '[)'), 1, :status,
                    :payment, :priceKind, :cents, :cancelUntil, :subject, 'Ánxela Núñez',
                    'ana@example.org', 'gl', :cancelledAt, :cancelledBy, now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("code", code())
        .param("f", facility)
        .param("u", use)
        .param("start", utc(start))
        .param("end", utc(start.plus(Duration.ofMinutes(minutes))))
        .param("status", status)
        .param("payment", payment)
        .param("priceKind", cents == null ? "FREE" : "PAID")
        .param("cents", cents, java.sql.Types.INTEGER)
        .param("cancelUntil", utc(start.minus(Duration.ofHours(24))))
        .param("subject", subject)
        .param(
            "cancelledAt",
            cancelledAt == null ? null : utc(cancelledAt),
            java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param("cancelledBy", cancelledAt == null ? null : "sub-desk", java.sql.Types.VARCHAR)
        .update();
    return booking;
  }

  /** Un código distinto por reserva: {@code FUR-} e catro letras do alfabeto do código. */
  String code() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    int n = codes.getAndIncrement();
    StringBuilder code = new StringBuilder("FUR-");
    for (int i = 0; i < 4; i++) {
      code.append(alphabet.charAt(n % alphabet.length()));
      n /= alphabet.length();
    }
    return code.toString();
  }

  static OffsetDateTime utc(Instant at) {
    return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
  }
}
