// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.OpenMatchPlayer;
import gal.furar.booking.domain.OpenMatchPlayerStatus;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Quen se une ás partidas abertas en SQL (ADR-0040): {@code on conflict do nothing} para «unha
 * persoa, unha vez». Sempre dentro dunha transacción (RLS), baixo o bloqueo da fila da partida, e
 * co concello como parámetro (A8b, {@code OpenMatchPlayerSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class OpenMatchPlayerSql {

  private final JdbcClient jdbc;

  OpenMatchPlayerSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  boolean insert(UUID municipality, OpenMatchPlayer p) {
    return jdbc.sql(
            """
            insert into booking_open_match_player
              (id, municipality_id, match_id, account_subject, email, name, language, status,
               created_at, decided_at)
            values (:id, :tenant, :match, :subject, :email, :name, :language, :status, :at,
                    :decided)
            on conflict do nothing
            returning id
            """)
        .param("id", p.id())
        .param("tenant", municipality)
        .param("match", p.matchId())
        .param("subject", p.accountSubject())
        .param("email", p.email())
        .param("name", p.name())
        .param("language", p.language().code())
        .param("status", p.status().name())
        .param("at", BookingSql.utc(p.createdAt()))
        .param(
            "decided",
            p.decidedAt() == null ? null : BookingSql.utc(p.decidedAt()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  Optional<OpenMatchPlayer> ofAccount(UUID municipality, UUID match, String subject) {
    return jdbc.sql(
            """
            select id, match_id, account_subject, email, name, language, status, created_at,
                   decided_at
              from booking_open_match_player
             where match_id = :match and account_subject = :subject
               and municipality_id = :tenant
            """)
        .param("match", match)
        .param("subject", subject)
        .param("tenant", municipality)
        .query((rs, n) -> player(rs))
        .optional();
  }

  Optional<OpenMatchPlayer> byId(UUID municipality, UUID match, UUID id) {
    return jdbc.sql(
            """
            select id, match_id, account_subject, email, name, language, status, created_at,
                   decided_at
              from booking_open_match_player
             where id = :id and match_id = :match
               and municipality_id = :tenant
            """)
        .param("id", id)
        .param("match", match)
        .param("tenant", municipality)
        .query((rs, n) -> player(rs))
        .optional();
  }

  List<OpenMatchPlayer> ofMatch(UUID municipality, UUID match) {
    return jdbc.sql(
            """
            select id, match_id, account_subject, email, name, language, status, created_at,
                   decided_at
              from booking_open_match_player
             where match_id = :match
               and municipality_id = :tenant
             order by created_at, id
            """)
        .param("match", match)
        .param("tenant", municipality)
        .query((rs, n) -> player(rs))
        .list();
  }

  /** Decide unha solicitude que segue pendente; devolve as filas tocadas (0 ou 1). */
  int decide(UUID municipality, UUID match, UUID id, OpenMatchPlayerStatus status, Instant at) {
    return jdbc.sql(
            """
            update booking_open_match_player
               set status = :status, decided_at = :at
             where id = :id and match_id = :match and status = 'PENDING'
               and municipality_id = :tenant
            """)
        .param("status", status.name())
        .param("at", BookingSql.utc(at))
        .param("id", id)
        .param("match", match)
        .param("tenant", municipality)
        .update();
  }

  int leave(UUID municipality, UUID match, String subject) {
    return jdbc.sql(
            """
            delete from booking_open_match_player
             where match_id = :match and account_subject = :subject
               and municipality_id = :tenant
            """)
        .param("match", match)
        .param("subject", subject)
        .param("tenant", municipality)
        .update();
  }

  int deletePending(UUID municipality, UUID match) {
    return jdbc.sql(
            """
            delete from booking_open_match_player
             where match_id = :match and status = 'PENDING'
               and municipality_id = :tenant
            """)
        .param("match", match)
        .param("tenant", municipality)
        .update();
  }

  private static OpenMatchPlayer player(ResultSet rs) throws SQLException {
    OffsetDateTime decided = rs.getObject("decided_at", OffsetDateTime.class);
    return new OpenMatchPlayer(
        rs.getObject("id", UUID.class),
        rs.getObject("match_id", UUID.class),
        rs.getString("account_subject"),
        rs.getString("email"),
        rs.getString("name"),
        Language.fromCode(rs.getString("language")).orElseThrow(),
        OpenMatchPlayerStatus.valueOf(rs.getString("status")),
        BookingSql.instant(rs, "created_at"),
        decided == null ? null : decided.toInstant());
  }
}
