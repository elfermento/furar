// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.ParticipantKind;
import gal.furar.shared.persistence.NativeSql;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As persoas de cada reserva por prazas en SQL (ADR-0031, punto 5): {@code booking_participant}
 * leva a exclusión «unha praza por persoa». Sempre dentro dunha transacción (RLS) e co concello
 * como parámetro (A8b, {@code BookingParticipantSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingParticipantSql {

  private final JdbcClient jdbc;

  BookingParticipantSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Unha fila activa, co uso e o período da súa reserva. */
  void insert(
      UUID municipality,
      UUID id,
      UUID bookingId,
      UUID useId,
      Instant start,
      Instant end,
      Participant participant,
      String personKey,
      int position) {
    jdbc.sql(
            """
            insert into booking_participant
              (id, municipality_id, booking_id, use_id, period, kind, person_key, dependent_id,
               name, birth_year, position)
            values (:id, :tenant, :booking, :use, tstzrange(:since, :until, '[)'), :kind, :key,
                    :dependent, :name, :birthYear, :position)
            """)
        .param("id", id)
        .param("tenant", municipality)
        .param("booking", bookingId)
        .param("use", useId)
        .param("since", BookingSql.utc(start))
        .param("until", BookingSql.utc(end))
        .param("kind", participant.kind().name())
        .param("key", personKey)
        .param("dependent", participant.dependentId(), Types.OTHER)
        .param("name", participant.name(), Types.VARCHAR)
        .param("birthYear", participant.birthYear(), Types.INTEGER)
        .param("position", position)
        .update();
  }

  /** Ao cancelar: as persoas da reserva deixan a súa praza. */
  int deactivate(UUID municipality, UUID bookingId) {
    return jdbc.sql(
            """
            update booking_participant set active = false
            where booking_id = :booking and active and municipality_id = :tenant
            """)
        .param("booking", bookingId)
        .param("tenant", municipality)
        .update();
  }

  /** Ao cambiar: as persoas de antes deixan a praza e quedan substituídas polas novas. */
  int replace(UUID municipality, UUID bookingId) {
    return jdbc.sql(
            """
            update booking_participant set active = false, replaced = true
            where booking_id = :booking and active and municipality_id = :tenant
            """)
        .param("booking", bookingId)
        .param("tenant", municipality)
        .update();
  }

  /**
   * De {@code keys}, as persoas que xa teñen praza nese uso nun período que pisa {@code [from,
   * to)}, sen contar a reserva {@code except}. Usa a exclusión GiST {@code
   * booking_participant_one_seat}.
   */
  List<String> seated(
      UUID municipality,
      UUID useId,
      Collection<String> keys,
      Instant from,
      Instant to,
      UUID except) {
    if (keys.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select distinct person_key from booking_participant
            where use_id = :use and person_key in (:keys) and active
              and period && tstzrange(:since, :until, '[)')
              and booking_id <> :skipped
              and municipality_id = :tenant
            """)
        .param("use", useId)
        .param("keys", List.copyOf(keys))
        .param("since", BookingSql.utc(from))
        .param("until", BookingSql.utc(to))
        .param("skipped", except)
        .param("tenant", municipality)
        .query(String.class)
        .list();
  }

  /**
   * As persoas destas reservas, sen as que un cambio substituíu: nunha confirmada, as que ocupan
   * praza; nunha cancelada, as que levaba ao cancelar. Por reserva e posición.
   */
  List<Row> of(UUID municipality, Collection<UUID> bookingIds) {
    if (bookingIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select booking_id, kind, dependent_id, name, birth_year
            from booking_participant
            where booking_id in (:bookings) and not replaced
              and municipality_id = :tenant
            order by booking_id, position, id
            """)
        .param("bookings", List.copyOf(bookingIds))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new Row(
                    rs.getObject("booking_id", UUID.class),
                    new Participant(
                        ParticipantKind.valueOf(rs.getString("kind")),
                        rs.getObject("dependent_id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("birth_year", Integer.class))))
        .list();
  }

  /** O titular pasa a identificarse por outro correo (o persoal corrixiuno). */
  int rekeyHolder(UUID municipality, UUID bookingId, String personKey) {
    return jdbc.sql(
            """
            update booking_participant set person_key = :key
            where booking_id = :booking and kind = 'HOLDER' and active
              and municipality_id = :tenant
            """)
        .param("key", personKey)
        .param("booking", bookingId)
        .param("tenant", municipality)
        .update();
  }

  /** O titular queda sen correo: xa non se compara con ningunha outra reserva. */
  int unkeyHolder(UUID municipality, UUID bookingId) {
    return jdbc.sql(
            """
            update booking_participant set person_key = 'w:' || id
            where booking_id = :booking and kind = 'HOLDER' and active
              and municipality_id = :tenant
            """)
        .param("booking", bookingId)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Conservación: as persoas das reservas anonimizadas quedan sen nome, sen ano e sen ficha, e
   * soltan a súa praza.
   */
  int anonymise(UUID municipality, Collection<UUID> bookingIds) {
    return jdbc.sql(
            """
            update booking_participant
            set person_key = 'x:' || id, active = false, name = null, birth_year = null,
                dependent_id = null
            where booking_id in (:bookings) and municipality_id = :tenant
            """)
        .param("bookings", List.copyOf(bookingIds))
        .param("tenant", municipality)
        .update();
  }

  /** Unha persoa coa súa reserva. */
  record Row(UUID bookingId, Participant participant) {}
}
