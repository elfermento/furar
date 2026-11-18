// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.application.port.out.PendingJoinRequests;
import gal.furar.notification.domain.JoinRequest;
import gal.furar.notification.domain.PendingJoinRequest;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As solicitudes de alta que esperan un reintento do correo, en SQL (ADR-0042, revisión do
 * 2026-11-17). Táboa {@code join_request}, sen concello ({@code TenancyAllowlist}).
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "Unha solicitude de alta é dun concello que aínda non está na instalación: a táboa non ten"
            + " municipality_id e só a usan o formulario público e a tarefa de reintento")
class JoinRequestSql implements PendingJoinRequests {

  private final JdbcClient jdbc;

  JoinRequestSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(PendingJoinRequest pending, Instant next) {
    JoinRequest r = pending.request();
    jdbc.sql(
            """
            insert into join_request
              (id, municipality, name, role, email, phone, message, language, received_at,
               attempts, next_attempt_at)
            values (:id, :municipality, :name, :role, :email, :phone, :message, :language,
                    :received, :attempts, :next)
            """)
        .param("id", pending.id())
        .param("municipality", r.municipality())
        .param("name", r.name())
        .param("role", r.role(), Types.VARCHAR)
        .param("email", r.email())
        .param("phone", r.phone())
        .param("message", r.message(), Types.VARCHAR)
        .param("language", pending.language().code())
        .param("received", utc(pending.receivedAt()))
        .param("attempts", pending.attempts())
        .param("next", utc(next))
        .update();
  }

  @Override
  public List<PendingJoinRequest> due(Instant now, int limit) {
    return jdbc.sql(
            """
            select id, municipality, name, role, email, phone, message, language, received_at,
                   attempts
              from join_request
             where next_attempt_at <= :now
             order by received_at
             limit :limit
            """)
        .param("now", utc(now))
        .param("limit", limit)
        .query(JoinRequestSql::pending)
        .list();
  }

  @Override
  public boolean claim(UUID id, Instant now, Instant next) {
    return jdbc.sql(
                """
                update join_request
                   set attempts = attempts + 1, next_attempt_at = :next
                 where id = :id and next_attempt_at <= :now
                """)
            .param("next", utc(next))
            .param("id", id)
            .param("now", utc(now))
            .update()
        == 1;
  }

  @Override
  public void delete(UUID id) {
    jdbc.sql("delete from join_request where id = :id").param("id", id).update();
  }

  @Override
  public int purgeReceivedBefore(Instant cutoff) {
    return jdbc.sql("delete from join_request where received_at < :cutoff")
        .param("cutoff", utc(cutoff))
        .update();
  }

  private static PendingJoinRequest pending(ResultSet rs, int n) throws SQLException {
    return new PendingJoinRequest(
        rs.getObject("id", UUID.class),
        new JoinRequest(
            rs.getString("municipality"),
            rs.getString("name"),
            rs.getString("role"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("message")),
        Language.fromCode(rs.getString("language")).orElse(Language.GL),
        rs.getObject("received_at", OffsetDateTime.class).toInstant(),
        rs.getInt("attempts"));
  }

  private static OffsetDateTime utc(Instant at) {
    return at.atOffset(ZoneOffset.UTC);
  }
}
