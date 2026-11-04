// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingEventKind;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.CapPeriod;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.booking.domain.Price;
import gal.furar.booking.domain.PriceKind;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As reservas en SQL (ADR-0025): a táboa usa o que JPA non expresa ben ({@code tstzrange}, as
 * exclusións e, ao reservar, {@code on conflict} e {@code for update} en {@code use_slot}), así que
 * o módulo non ten entidades JPA. Sempre dentro dunha transacción (RLS) e co concello como
 * parámetro (A8b, {@code BookingSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingSql {

  /** Ningunha reserva ten este id: «sen excepción» no límite e na praza. */
  private static final UUID NONE = new UUID(0, 0);

  private final JdbcClient jdbc;

  BookingSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Booking> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_language, cancelled_at,
                   cancelled_by, created_at, updated_at, version
            from booking
            where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> booking(rs))
        .optional();
  }

  /** A reserva do concello se é desta persoa: a doutra non existe para ela. */
  Optional<Booking> findOfHolder(UUID municipality, UUID id, String subject) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_language, cancelled_at,
                   cancelled_by, created_at, updated_at, version
            from booking
            where id = :id and holder_subject = :subject and municipality_id = :tenant
            """)
        .param("id", id)
        .param("subject", subject)
        .param("tenant", municipality)
        .query((rs, n) -> booking(rs))
        .optional();
  }

  /**
   * As próximas da persoa (confirmadas que aínda non remataron), da máis próxima á máis lonxana.
   */
  List<Booking> upcoming(UUID municipality, String subject, Instant now, int limit, long offset) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_language, cancelled_at,
                   cancelled_by, created_at, updated_at, version
            from booking
            where holder_subject = :subject and status = 'CONFIRMED' and upper(period) > :now
              and municipality_id = :tenant
            order by lower(period), id
            limit :limit offset :offset
            """)
        .param("subject", subject)
        .param("now", utc(now))
        .param("limit", limit)
        .param("offset", offset)
        .param("tenant", municipality)
        .query((rs, n) -> booking(rs))
        .list();
  }

  long countUpcoming(UUID municipality, String subject, Instant now) {
    return jdbc.sql(
            """
            select count(*) from booking
            where holder_subject = :subject and status = 'CONFIRMED' and upper(period) > :now
              and municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  /** O historial da persoa (realizadas e canceladas), da máis recente á máis antiga. */
  List<Booking> history(UUID municipality, String subject, Instant now, int limit, long offset) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_language, cancelled_at,
                   cancelled_by, created_at, updated_at, version
            from booking
            where holder_subject = :subject
              and (status <> 'CONFIRMED' or upper(period) <= :now)
              and municipality_id = :tenant
            order by lower(period) desc, id desc
            limit :limit offset :offset
            """)
        .param("subject", subject)
        .param("now", utc(now))
        .param("limit", limit)
        .param("offset", offset)
        .param("tenant", municipality)
        .query((rs, n) -> booking(rs))
        .list();
  }

  long countHistory(UUID municipality, String subject, Instant now) {
    return jdbc.sql(
            """
            select count(*) from booking
            where holder_subject = :subject
              and (status <> 'CONFIRMED' or upper(period) <= :now)
              and municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  /**
   * As confirmadas da instalación que non remataron en {@code now}, dun uso ou de todos ({@code
   * null}), que pisan {@code [from, to)}; da primeira á última. Usa {@code booking_facility_idx}.
   */
  List<Booking> confirmedNotEnded(
      UUID municipality,
      UUID facilityId,
      @Nullable UUID useId,
      Instant now,
      Instant from,
      Instant to) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_language, cancelled_at,
                   cancelled_by, created_at, updated_at, version
            from booking
            where facility_id = :facility and status = 'CONFIRMED' and upper(period) > :now
              and (:allUses or use_id = :use)
              and period && tstzrange(:since, :until, '[)')
              and municipality_id = :tenant
            order by lower(period), id
            """)
        .param("facility", facilityId)
        .param("allUses", useId == null)
        .param("use", useId == null ? NONE : useId)
        .param("now", utc(now))
        .param("since", utc(from))
        .param("until", utc(to))
        .param("tenant", municipality)
        .query((rs, n) -> booking(rs))
        .list();
  }

  /** Se a instalación tivo algunha vez unha reserva (tamén cancelada ou pasada). */
  boolean anyOf(UUID municipality, UUID facilityId) {
    return jdbc.sql(
            """
            select 1 from booking
            where facility_id = :facility and municipality_id = :tenant
            limit 1
            """)
        .param("facility", facilityId)
        .param("tenant", municipality)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  /**
   * As reservas confirmadas da persoa nese uso que empezan en {@code [from, to)}: o límite por
   * persoa (§2.3, P4), sen contar {@code except} (a que se cambia). Usa o índice {@code
   * booking_holder_idx}.
   */
  int countConfirmed(
      UUID municipality,
      UUID useId,
      String subject,
      Instant from,
      Instant to,
      @Nullable UUID except) {
    return jdbc.sql(
            """
            select count(*) from booking
            where use_id = :use and holder_subject = :subject and status = 'CONFIRMED'
              and lower(period) >= :since and lower(period) < :until
              and id <> :skipped
              and municipality_id = :tenant
            """)
        .param("use", useId)
        .param("skipped", except == null ? NONE : except)
        .param("subject", subject)
        .param("since", utc(from))
        .param("until", utc(to))
        .param("tenant", municipality)
        .query(Integer.class)
        .single();
  }

  /**
   * Se a persoa xa ten unha reserva confirmada nese uso que pisa {@code [from, to)}, sen contar
   * {@code except} (a que se cambia).
   */
  boolean holds(
      UUID municipality,
      UUID useId,
      String subject,
      Instant from,
      Instant to,
      @Nullable UUID except) {
    return jdbc.sql(
                """
            select count(*) from booking
            where use_id = :use and holder_subject = :subject and status = 'CONFIRMED'
              and period && tstzrange(:since, :until, '[)')
              and id <> :skipped
              and municipality_id = :tenant
            """)
            .param("use", useId)
            .param("skipped", except == null ? NONE : except)
            .param("subject", subject)
            .param("since", utc(from))
            .param("until", utc(to))
            .param("tenant", municipality)
            .query(Integer.class)
            .single()
        > 0;
  }

  /**
   * Garda a fila {@code booking} dunha reserva nova (versión 0).
   *
   * @return falso se o código xa existe no concello: quen chama xera outro
   */
  boolean insertBooking(UUID municipality, Booking b) {
    BookingRules rules = b.rules();
    return jdbc.sql(
                """
                insert into booking
                  (id, municipality_id, code, facility_id, use_id, part_id, rental_mode, period,
                   slots, status, payment_status, price_kind, amount_cents, cancel_until,
                   rule_min_advance_hours, rule_max_advance_days, rule_max_slots, rule_cap_count,
                   rule_cap_period, rule_cancel_hours, holder_subject, holder_name, holder_email,
                   holder_language, created_at, updated_at, version)
                values
                  (:id, :tenant, :code, :facility, :use, :part, :mode,
                   tstzrange(:since, :until, '[)'), :slots, :status, :payment, :priceKind,
                   :amount, :cancelUntil, :minAdvance, :maxAdvance, :maxSlots, :capCount,
                   :capPeriod, :cancelHours, :subject, :name, :email, :language, :createdAt,
                   :updatedAt, 0)
                on conflict (municipality_id, code) do nothing
                """)
            .param("id", b.id().value())
            .param("tenant", municipality)
            .param("code", b.code().value())
            .param("facility", b.slot().facilityId())
            .param("use", b.slot().useId())
            .param("part", b.slot().partId(), Types.OTHER)
            .param("mode", b.slot().rentalMode().name())
            .param("since", utc(b.slot().start()))
            .param("until", utc(b.slot().end()))
            .param("slots", b.slot().slots())
            .param("status", b.status().name())
            .param("payment", b.paymentStatus().name())
            .param("priceKind", b.price().kind().name())
            .param("amount", b.price().amountCents(), Types.INTEGER)
            .param("cancelUntil", utc(b.cancelUntil()))
            .param("minAdvance", rules.minAdvanceHours(), Types.INTEGER)
            .param("maxAdvance", rules.maxAdvanceDays(), Types.INTEGER)
            .param("maxSlots", rules.maxSlots(), Types.INTEGER)
            .param("capCount", rules.capCount(), Types.INTEGER)
            .param(
                "capPeriod",
                rules.capPeriod() == null ? null : rules.capPeriod().name(),
                Types.VARCHAR)
            .param("cancelHours", rules.cancelHours(), Types.INTEGER)
            .param("subject", b.requireHolder().subject())
            .param("name", b.requireHolder().name())
            .param("email", b.requireHolder().email())
            .param("language", b.requireHolder().language().code())
            .param("createdAt", utc(b.createdAt()))
            .param("updatedAt", utc(b.updatedAt()))
            .update()
        == 1;
  }

  /** A ocupación activa da reserva (ADR-0012): as exclusións da táboa son a última garantía. */
  void insertOccupancy(UUID municipality, Booking b) {
    jdbc.sql(
            """
            insert into booking_occupancy
              (id, municipality_id, booking_id, facility_id, use_id, part_id, rental_mode,
               period)
            values (:id, :tenant, :booking, :facility, :use, :part, :mode,
                    tstzrange(:since, :until, '[)'))
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("booking", b.id().value())
        .param("facility", b.slot().facilityId())
        .param("use", b.slot().useId())
        .param("part", b.slot().partId(), Types.OTHER)
        .param("mode", b.slot().rentalMode().name())
        .param("since", utc(b.slot().start()))
        .param("until", utc(b.slot().end()))
        .update();
  }

  /**
   * Unha praza máis na franxa que empeza en {@code slotStart} (ADR-0012): crea o contador se non
   * existe e súmalle un; o {@code CHECK} de capacidade fai imposible pasarse.
   */
  void takeSeat(UUID municipality, UUID useId, Instant slotStart, int capacity) {
    jdbc.sql(
            """
            insert into use_slot (municipality_id, use_id, slot_start, capacity)
            values (:tenant, :use, :start, :capacity)
            on conflict do nothing
            """)
        .param("tenant", municipality)
        .param("use", useId)
        .param("start", utc(slotStart))
        .param("capacity", capacity)
        .update();
    jdbc.sql(
            """
            update use_slot set taken = taken + 1
            where use_id = :use and slot_start = :start and municipality_id = :tenant
            """)
        .param("use", useId)
        .param("start", utc(slotStart))
        .param("tenant", municipality)
        .update();
  }

  /**
   * Garda os cambios dunha reserva que xa existe (cambiar, cancelar, cobrar) e sobe a versión.
   *
   * @return falso se a versión xa non é {@code expectedVersion}: outra escritura chegou antes
   */
  boolean updateBooking(UUID municipality, Booking b, long expectedVersion) {
    BookingRules rules = b.rules();
    return jdbc.sql(
                """
                update booking set
                  facility_id = :facility, use_id = :use, part_id = :part, rental_mode = :mode,
                  period = tstzrange(:since, :until, '[)'), slots = :slots, status = :status,
                  payment_status = :payment, price_kind = :priceKind, amount_cents = :amount,
                  cancel_until = :cancelUntil, rule_min_advance_hours = :minAdvance,
                  rule_max_advance_days = :maxAdvance, rule_max_slots = :maxSlots,
                  rule_cap_count = :capCount, rule_cap_period = :capPeriod,
                  rule_cancel_hours = :cancelHours, cancelled_at = :cancelledAt,
                  cancelled_by = :cancelledBy, updated_at = :updatedAt, version = version + 1
                where id = :id and version = :version and municipality_id = :tenant
                """)
            .param("id", b.id().value())
            .param("version", expectedVersion)
            .param("tenant", municipality)
            .param("facility", b.slot().facilityId())
            .param("use", b.slot().useId())
            .param("part", b.slot().partId(), Types.OTHER)
            .param("mode", b.slot().rentalMode().name())
            .param("since", utc(b.slot().start()))
            .param("until", utc(b.slot().end()))
            .param("slots", b.slot().slots())
            .param("status", b.status().name())
            .param("payment", b.paymentStatus().name())
            .param("priceKind", b.price().kind().name())
            .param("amount", b.price().amountCents(), Types.INTEGER)
            .param("cancelUntil", utc(b.cancelUntil()))
            .param("minAdvance", rules.minAdvanceHours(), Types.INTEGER)
            .param("maxAdvance", rules.maxAdvanceDays(), Types.INTEGER)
            .param("maxSlots", rules.maxSlots(), Types.INTEGER)
            .param("capCount", rules.capCount(), Types.INTEGER)
            .param(
                "capPeriod",
                rules.capPeriod() == null ? null : rules.capPeriod().name(),
                Types.VARCHAR)
            .param("cancelHours", rules.cancelHours(), Types.INTEGER)
            .param(
                "cancelledAt",
                b.cancelledAt() == null ? null : utc(b.cancelledAt()),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .param("cancelledBy", b.cancelledBy(), Types.VARCHAR)
            .param("updatedAt", utc(b.updatedAt()))
            .update()
        == 1;
  }

  /** Deixa a ocupación da reserva inactiva (ao cancelar ou cambiar): o oco queda libre. */
  int deactivateOccupancy(UUID municipality, UUID bookingId) {
    return jdbc.sql(
            """
            update booking_occupancy set active = false
            where booking_id = :booking and active and municipality_id = :tenant
            """)
        .param("booking", bookingId)
        .param("tenant", municipality)
        .update();
  }

  /** Unha praza menos na franxa que empeza en {@code slotStart} (ADR-0012). */
  int releaseSeat(UUID municipality, UUID useId, Instant slotStart) {
    return jdbc.sql(
            """
            update use_slot set taken = taken - 1
            where use_id = :use and slot_start = :start and municipality_id = :tenant
            """)
        .param("use", useId)
        .param("start", utc(slotStart))
        .param("tenant", municipality)
        .update();
  }

  /**
   * As prazas novas do uso nos contadores das franxas que empezan desde {@code from} (ADR-0012). O
   * {@code CHECK} segue valendo: baixar por debaixo das tomadas xa o rexeitou {@code facility}
   * (P1).
   */
  int resizeSeats(UUID municipality, UUID useId, int capacity, Instant from) {
    return jdbc.sql(
            """
            update use_slot set capacity = :capacity
            where use_id = :use and slot_start >= :since and municipality_id = :tenant
            """)
        .param("capacity", capacity)
        .param("use", useId)
        .param("since", utc(from))
        .param("tenant", municipality)
        .update();
  }

  /** A fila {@code CHANGED} da historia, co período de antes e o de despois. */
  void insertChangeEvent(
      UUID municipality, UUID bookingId, Instant at, String actor, BookedSlot from, BookedSlot to) {
    jdbc.sql(
            """
            insert into booking_event
              (id, municipality_id, booking_id, kind, at, actor_subject, from_period, to_period)
            values (:id, :tenant, :booking, 'CHANGED', :at, :actor,
                    tstzrange(:fromStart, :fromEnd, '[)'), tstzrange(:toStart, :toEnd, '[)'))
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("booking", bookingId)
        .param("at", utc(at))
        .param("actor", actor)
        .param("fromStart", utc(from.start()))
        .param("fromEnd", utc(from.end()))
        .param("toStart", utc(to.start()))
        .param("toEnd", utc(to.end()))
        .update();
  }

  /** Unha fila da historia da reserva (só se insire). */
  void insertEvent(
      UUID municipality, UUID bookingId, BookingEventKind kind, Instant at, String actor) {
    jdbc.sql(
            """
            insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
            values (:id, :tenant, :booking, :kind, :at, :actor)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("booking", bookingId)
        .param("kind", kind.name())
        .param("at", utc(at))
        .param("actor", actor)
        .update();
  }

  /**
   * Unha fila da historia dunha cancelación do concello coas canles do aviso (P12; só se insire).
   */
  void insertCancelEvent(
      UUID municipality, UUID bookingId, Instant at, String actor, Collection<String> channels) {
    jdbc.sql(
            """
            insert into booking_event
              (id, municipality_id, booking_id, kind, at, actor_subject, channels)
            values (:id, :tenant, :booking, 'CANCELLED_BY_MUNICIPALITY', :at, :actor,
                    string_to_array(:channels, ','))
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("booking", bookingId)
        .param("at", utc(at))
        .param("actor", actor)
        .param("channels", String.join(",", channels.stream().sorted().toList()))
        .update();
  }

  /**
   * As ocupacións activas destas instalacións que pisan {@code [from, to)}, nunha consulta
   * (§1.2.18). Usa a exclusión GiST de {@code booking_occupancy} sobre {@code period}.
   */
  List<FacilityOccupied> activeOccupancy(
      UUID municipality, Collection<UUID> facilityIds, Instant from, Instant to) {
    if (facilityIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select facility_id, use_id, part_id, lower(period) as start_at,
                   upper(period) as end_at
            from booking_occupancy
            where municipality_id = :tenant and active and facility_id in (:facilities)
              and period && tstzrange(:since, :until, '[)')
            order by facility_id, lower(period)
            """)
        .param("tenant", municipality)
        .param("facilities", List.copyOf(facilityIds))
        .param("since", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
        .param("until", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
        .query(
            (rs, n) ->
                new FacilityOccupied(
                    rs.getObject("facility_id", UUID.class),
                    new Occupied(
                        rs.getObject("use_id", UUID.class),
                        rs.getObject("part_id", UUID.class),
                        instant(rs, "start_at"),
                        instant(rs, "end_at"))))
        .list();
  }

  /** Unha ocupación coa súa instalación. */
  record FacilityOccupied(UUID facilityId, Occupied occupied) {}

  /** Unha fila de {@code booking} coas columnas de {@link #find}. */
  static Booking booking(ResultSet rs) throws SQLException {
    BookedSlot slot =
        new BookedSlot(
            rs.getObject("facility_id", UUID.class),
            rs.getObject("use_id", UUID.class),
            rs.getObject("part_id", UUID.class),
            RentalMode.valueOf(rs.getString("rental_mode")),
            instant(rs, "start_at"),
            instant(rs, "end_at"),
            rs.getInt("slots"));
    String capPeriod = rs.getString("rule_cap_period");
    BookingRules rules =
        new BookingRules(
            integer(rs, "rule_min_advance_hours"),
            integer(rs, "rule_max_advance_days"),
            integer(rs, "rule_max_slots"),
            integer(rs, "rule_cap_count"),
            capPeriod == null ? null : CapPeriod.valueOf(capPeriod),
            integer(rs, "rule_cancel_hours"));
    // Anonimizada (conservación): sen persoa.
    String subject = rs.getString("holder_subject");
    Holder holder =
        subject == null
            ? null
            : new Holder(
                subject,
                rs.getString("holder_name"),
                rs.getString("holder_email"),
                Language.fromCode(rs.getString("holder_language")).orElseThrow());
    return Booking.rehydrate(
        new BookingId(rs.getObject("id", UUID.class)),
        new BookingCode(rs.getString("code")),
        slot,
        BookingStatus.valueOf(rs.getString("status")),
        PaymentStatus.valueOf(rs.getString("payment_status")),
        new Price(PriceKind.valueOf(rs.getString("price_kind")), integer(rs, "amount_cents")),
        rules,
        instant(rs, "cancel_until"),
        holder,
        nullableInstant(rs, "cancelled_at"),
        rs.getString("cancelled_by"),
        rs.getLong("version"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  static OffsetDateTime utc(Instant at) {
    return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
  }

  static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  private static @Nullable Instant nullableInstant(ResultSet rs, String column)
      throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static @Nullable Integer integer(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, Integer.class);
  }
}
