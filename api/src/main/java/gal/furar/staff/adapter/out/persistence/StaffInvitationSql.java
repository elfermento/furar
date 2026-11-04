// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.staff.domain.InvitationDelivery;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffInvitationId;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.TokenHash;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As invitacións do persoal en SQL (ADR-0027, plan do dominio 4, §1.2.6 e §1.2.7). Só coñece o hash
 * do token, nunca o token. Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b,
 * {@code StaffInvitationSqlTenantIT}): un token doutro concello non se atopa.
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class StaffInvitationSql {

  private final JdbcClient jdbc;

  StaffInvitationSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(UUID municipality, StaffInvitation i) {
    jdbc.sql(
            """
            insert into staff_invitation
              (id, municipality_id, member_id, token_hash, requested_by, created_at, expires_at,
               used_at, superseded_at, delivery)
            values (:id, :tenant, :member, :hash, :requestedBy, :createdAt, :expiresAt, :usedAt,
                    :supersededAt, :delivery)
            """)
        .param("id", i.id().value())
        .param("tenant", municipality)
        .param("member", i.memberId().value())
        .param("hash", i.tokenHash().bytes())
        .param("requestedBy", i.requestedBy())
        .param("createdAt", utc(i.createdAt()))
        .param("expiresAt", utc(i.expiresAt()))
        .param("usedAt", utc(i.usedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("supersededAt", utc(i.supersededAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("delivery", i.delivery().name())
        .update();
  }

  /** A invitación deste hash no concello, estea como estea (o dominio decide se vale). */
  Optional<StaffInvitation> findByHash(UUID municipality, TokenHash hash) {
    return jdbc.sql(
            """
            select id, member_id, token_hash, requested_by, created_at, expires_at, used_at,
                   superseded_at, delivery
              from staff_invitation
             where token_hash = :hash and municipality_id = :tenant
            """)
        .param("hash", hash.bytes())
        .param("tenant", municipality)
        .query(StaffInvitationSql::invitation)
        .optional();
  }

  /** A última invitación de cada pertenza do concello, para a lista do persoal. */
  List<StaffInvitation> latestPerMember(UUID municipality) {
    return jdbc.sql(
            """
            select distinct on (member_id)
                   id, member_id, token_hash, requested_by, created_at, expires_at, used_at,
                   superseded_at, delivery
              from staff_invitation
             where municipality_id = :tenant
             order by member_id, created_at desc, id desc
            """)
        .param("tenant", municipality)
        .query(StaffInvitationSql::invitation)
        .list();
  }

  /** A última invitación dunha pertenza, se ten algunha. */
  Optional<StaffInvitation> latestOf(UUID municipality, UUID memberId) {
    return jdbc.sql(
            """
            select id, member_id, token_hash, requested_by, created_at, expires_at, used_at,
                   superseded_at, delivery
              from staff_invitation
             where member_id = :member and municipality_id = :tenant
             order by created_at desc, id desc
             limit 1
            """)
        .param("member", memberId)
        .param("tenant", municipality)
        .query(StaffInvitationSql::invitation)
        .optional();
  }

  /** Cantos envíos leva cada pertenza do concello (SendLimits, na lista do persoal). */
  Map<UUID, Integer> sendCountPerMember(UUID municipality) {
    Map<UUID, Integer> counts = new HashMap<>();
    jdbc.sql(
            """
            select member_id, count(*) as sends from staff_invitation
             where municipality_id = :tenant
             group by member_id
            """)
        .param("tenant", municipality)
        .query(
            rs -> {
              counts.put(rs.getObject("member_id", UUID.class), rs.getInt("sends"));
            });
    return counts;
  }

  /** Horas de todos os envíos dunha pertenza, do máis novo ao máis vello (SendLimits). */
  List<Instant> sendsOf(UUID municipality, UUID memberId) {
    return jdbc.sql(
            """
            select created_at from staff_invitation
             where member_id = :member and municipality_id = :tenant
             order by created_at desc
            """)
        .param("member", memberId)
        .param("tenant", municipality)
        .query((rs, n) -> instant(rs, "created_at"))
        .list();
  }

  /**
   * Horas dos envíos do concello despois de {@code since}, do máis novo ao máis vello e como moito
   * {@code limit} (SendLimits só precisa os 30 máis recentes).
   */
  List<Instant> sendsSince(UUID municipality, Instant since, int limit) {
    return jdbc.sql(
            """
            select created_at from staff_invitation
             where created_at > :since and municipality_id = :tenant
             order by created_at desc
             limit :limit
            """)
        .param("since", utc(since))
        .param("tenant", municipality)
        .param("limit", limit)
        .query((rs, n) -> instant(rs, "created_at"))
        .list();
  }

  /**
   * Substitúe a invitación aberta da pertenza (reenviar ou desactivar).
   *
   * @return as filas substituídas (0 ou 1: o índice único permite como moito unha aberta)
   */
  int supersedeOpen(UUID municipality, UUID memberId, Instant now) {
    return jdbc.sql(
            """
            update staff_invitation
               set superseded_at = greatest(:now, created_at)
             where member_id = :member and used_at is null and superseded_at is null
               and municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("member", memberId)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Borra as invitacións non aceptadas que caducaron antes de {@code cutoff} (§1.2.14): abertas e
   * substituídas. As usadas quedan, porque din cando se aceptou.
   *
   * @return os identificadores das borradas
   */
  List<UUID> deleteUnusedExpiredBefore(UUID municipality, Instant cutoff) {
    return jdbc.sql(
            """
            delete from staff_invitation
             where used_at is null and expires_at < :cutoff and municipality_id = :tenant
            returning id
            """)
        .param("cutoff", utc(cutoff))
        .param("tenant", municipality)
        .query((rs, n) -> rs.getObject("id", UUID.class))
        .list();
  }

  /** As invitacións destas pertenzas no concello. */
  List<UUID> idsOf(UUID municipality, Collection<UUID> members) {
    return jdbc.sql(
            """
            select id from staff_invitation
             where member_id in (:members) and municipality_id = :tenant
            """)
        .param("members", members)
        .param("tenant", municipality)
        .query((rs, n) -> rs.getObject("id", UUID.class))
        .list();
  }

  /**
   * Marca a invitación como usada se aínda vale (aberta e sen caducar).
   *
   * @return falso se outra aceptación, un reenvío ou unha desactivación chegaron antes, ou se
   *     caducou
   */
  boolean markUsed(UUID municipality, UUID id, Instant now) {
    return jdbc.sql(
                """
                update staff_invitation
                   set used_at = :now
                 where id = :id and used_at is null and superseded_at is null
                   and expires_at > :now and created_at <= :now and municipality_id = :tenant
                """)
            .param("now", utc(now))
            .param("id", id)
            .param("tenant", municipality)
            .update()
        == 1;
  }

  /**
   * Garda o resultado do correo dunha invitación pendente (despois do commit, noutra transacción).
   *
   * @return falso se a invitación xa tiña resultado
   */
  boolean recordDelivery(UUID municipality, UUID id, InvitationDelivery delivery) {
    return jdbc.sql(
                """
                update staff_invitation
                   set delivery = :delivery
                 where id = :id and delivery = 'PENDING' and municipality_id = :tenant
                """)
            .param("delivery", delivery.name())
            .param("id", id)
            .param("tenant", municipality)
            .update()
        == 1;
  }

  private static StaffInvitation invitation(ResultSet rs, int row) throws SQLException {
    return StaffInvitation.rehydrate(
        new StaffInvitationId(rs.getObject("id", UUID.class)),
        new StaffMemberId(rs.getObject("member_id", UUID.class)),
        TokenHash.of(rs.getBytes("token_hash")),
        rs.getString("requested_by"),
        instant(rs, "created_at"),
        instant(rs, "expires_at"),
        nullableInstant(rs, "used_at"),
        nullableInstant(rs, "superseded_at"),
        InvitationDelivery.valueOf(rs.getString("delivery")));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  private static @Nullable Instant nullableInstant(ResultSet rs, String column)
      throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
