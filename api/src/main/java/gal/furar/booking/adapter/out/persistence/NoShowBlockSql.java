// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As faltas que contan e os bloqueos por faltas en SQL (ADR-0032, punto 7): {@code
 * booking_no_show_block} e as reservas que causaron cada un ({@code booking_no_show_block_fault}).
 * Todo por correo normalizado e dentro do concello: unha falta nun concello non conta noutro.
 * Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * NoShowBlockSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class NoShowBlockSql {

  private final JdbcClient jdbc;

  NoShowBlockSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * As faltas dese correo que contan: as reservas {@code NO_SHOW} cuxa franxa rematou desde {@code
   * from} (a xanela e o momento en que se acendeu a penalización) e que non causaron un bloqueo sen
   * levantar (cumprido ou vixente). As dun bloqueo levantado seguen contando. Da máis vella á máis
   * nova. Usa o índice parcial {@code booking_no_show_email_idx}.
   */
  List<UUID> countingFaults(UUID municipality, String emailKey, Instant from) {
    return jdbc.sql(
            """
            select b.id from booking b
            where b.holder_email_key = :emailKey and b.attendance = 'NO_SHOW'
              and upper(b.period) >= :since
              and not exists (
                select 1 from booking_no_show_block_fault f
                join booking_no_show_block k
                  on k.municipality_id = f.municipality_id and k.id = f.block_id
                where f.booking_id = b.id and k.lifted_at is null
                  and f.municipality_id = :tenant)
              and b.municipality_id = :tenant
            order by upper(b.period), b.id
            """)
        .param("emailKey", emailKey)
        .param("since", BookingSql.utc(from))
        .param("tenant", municipality)
        .query(UUID.class)
        .list();
  }

  /** Un bloqueo novo, desde {@code startsAt} ata {@code endsAt}, coas faltas que o causaron. */
  void insert(
      UUID municipality,
      UUID id,
      String emailKey,
      Instant startsAt,
      Instant endsAt,
      Collection<UUID> faults) {
    jdbc.sql(
            """
            insert into booking_no_show_block
              (id, municipality_id, holder_email_key, starts_at, ends_at, created_at)
            values (:id, :tenant, :emailKey, :startsAt, :endsAt, :startsAt)
            """)
        .param("id", id)
        .param("tenant", municipality)
        .param("emailKey", emailKey)
        .param("startsAt", BookingSql.utc(startsAt))
        .param("endsAt", BookingSql.utc(endsAt))
        .update();
    for (UUID booking : faults) {
      jdbc.sql(
              """
              insert into booking_no_show_block_fault (municipality_id, block_id, booking_id)
              values (:tenant, :block, :booking)
              """)
          .param("tenant", municipality)
          .param("block", id)
          .param("booking", booking)
          .update();
    }
  }

  /**
   * Os correos de {@code emailKeys} cun bloqueo vixente e sen levantar en {@code now}: a quen non
   * se avisa dunha hora liberada (ADR-0033, punto 7). Unha consulta para todos.
   */
  List<String> activeAmong(UUID municipality, Collection<String> emailKeys, Instant now) {
    if (emailKeys.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select distinct holder_email_key from booking_no_show_block
            where holder_email_key in (:emailKeys) and lifted_at is null
              and starts_at <= :now and ends_at > :now
              and municipality_id = :tenant
            """)
        .param("emailKeys", List.copyOf(emailKeys))
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(String.class)
        .list();
  }

  /**
   * O bloqueo vixente e sen levantar dese correo en {@code now}, se o hai: o que remata máis tarde,
   * con cantas faltas o causaron. Usa o índice {@code booking_no_show_block_email_idx}.
   */
  Optional<Active> active(UUID municipality, String emailKey, Instant now) {
    return jdbc.sql(
            """
            select k.id, k.ends_at,
                   (select count(*) from booking_no_show_block_fault f
                    where f.block_id = k.id and f.municipality_id = :tenant) as faults
            from booking_no_show_block k
            where k.holder_email_key = :emailKey and k.lifted_at is null
              and k.starts_at <= :now and k.ends_at > :now
              and k.municipality_id = :tenant
            order by k.ends_at desc, k.id
            limit 1
            """)
        .param("emailKey", emailKey)
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new Active(
                    rs.getObject("id", UUID.class),
                    BookingSql.instant(rs, "ends_at"),
                    rs.getInt("faults")))
        .optional();
  }

  /**
   * Levanta os bloqueos vixentes que causou esa reserva (desfíxose a falta, validouse, cancelouna
   * ou cambiouna o persoal, ou corrixiuse o correo). Un bloqueo xa cumprido non se toca: as súas
   * faltas xa non contan.
   *
   * @return cantos levantou
   */
  int liftCausedBy(UUID municipality, UUID bookingId, Instant now, String actor) {
    return jdbc.sql(
            """
            update booking_no_show_block
            set lifted_at = :now, lifted_by = :actor
            where lifted_at is null and ends_at > :now
              and id in (select f.block_id from booking_no_show_block_fault f
                         where f.booking_id = :booking and f.municipality_id = :tenant)
              and municipality_id = :tenant
            """)
        .param("now", BookingSql.utc(now))
        .param("actor", actor)
        .param("booking", bookingId)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Os bloqueos que remataron antes de {@code cutoff} (conservación), do máis vello ao máis novo.
   */
  List<UUID> endedBefore(UUID municipality, Instant cutoff, int limit) {
    return jdbc.sql(
            """
            select id from booking_no_show_block
            where ends_at < :cutoff
              and municipality_id = :tenant
            order by ends_at, id
            limit :limit
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("tenant", municipality)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  /** Borra eses bloqueos coas reservas que os causaron (as reservas quedan). */
  int delete(UUID municipality, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    jdbc.sql(
            """
            delete from booking_no_show_block_fault
            where block_id in (:ids)
              and municipality_id = :tenant
            """)
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .update();
    return jdbc.sql(
            """
            delete from booking_no_show_block
            where id in (:ids)
              and municipality_id = :tenant
            """)
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .update();
  }

  /** Un bloqueo vixente: ata cando e cantas faltas o causaron. */
  record Active(UUID id, Instant endsAt, int faults) {}
}
