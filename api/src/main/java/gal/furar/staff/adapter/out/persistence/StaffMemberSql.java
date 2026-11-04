// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import gal.furar.staff.application.port.out.ActiveMembership;
import gal.furar.staff.application.port.out.RetainedMember;
import gal.furar.staff.domain.EmailAddress;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffRole;
import gal.furar.staff.domain.StaffStatus;
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
 * As pertenzas do persoal en SQL (ADR-0027, plan do dominio 4, §1.2.1): sen JPA, porque non son
 * contido portable do concello. Sempre dentro dunha transacción (RLS) e co concello como parámetro
 * (A8b, {@code StaffMemberSqlTenantIT}). Os índices únicos parciais (un correo non desactivado e
 * unha persoa activa por concello) son a última garantía; a alta usa {@code on conflict do
 * nothing}.
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class StaffMemberSql {

  private final JdbcClient jdbc;

  StaffMemberSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<StaffMember> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, email, display_name, subject, role, status, language, invited_by,
                   invited_at, accepted_at, deactivated_by, deactivated_at,
                   last_sign_in_at, previous_sign_in_at, version, created_at, updated_at
              from staff_member
             where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query(StaffMemberSql::member)
        .optional();
  }

  /** A pertenza bloqueada ata o fin da transacción, para cambiala. */
  Optional<StaffMember> findForUpdate(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, email, display_name, subject, role, status, language, invited_by,
                   invited_at, accepted_at, deactivated_by, deactivated_at,
                   last_sign_in_at, previous_sign_in_at, version, created_at, updated_at
              from staff_member
             where id = :id and municipality_id = :tenant
               for update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query(StaffMemberSql::member)
        .optional();
  }

  /**
   * As pertenzas do concello, todas ou só as dun estado, das máis novas ás máis vellas. Sen
   * paxinación: un concello ten poucas.
   */
  List<StaffMember> list(UUID municipality, @Nullable StaffStatus status) {
    return jdbc.sql(
            """
            select id, email, display_name, subject, role, status, language, invited_by,
                   invited_at, accepted_at, deactivated_by, deactivated_at,
                   last_sign_in_at, previous_sign_in_at, version, created_at, updated_at
              from staff_member
             where (:all or status = :status) and municipality_id = :tenant
             order by created_at desc, id
            """)
        .param("all", status == null)
        .param("status", status == null ? "" : status.name())
        .param("tenant", municipality)
        .query(StaffMemberSql::member)
        .list();
  }

  /**
   * As pertenzas destas persoas no concello, tamén as desactivadas: poñen nome e correo a cada
   * {@code sub} no rexistro de actividade.
   */
  List<StaffMember> findBySubjects(UUID municipality, Collection<String> subjects) {
    if (subjects.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select id, email, display_name, subject, role, status, language, invited_by,
                   invited_at, accepted_at, deactivated_by, deactivated_at,
                   last_sign_in_at, previous_sign_in_at, version, created_at, updated_at
              from staff_member
             where subject in (:subjects) and municipality_id = :tenant
             order by created_at desc, id
            """)
        .param("subjects", subjects)
        .param("tenant", municipality)
        .query(StaffMemberSql::member)
        .list();
  }

  /** Estas pertenzas do concello, tamén as desactivadas: o correo de quen recibe cada acción. */
  List<StaffMember> findByIds(UUID municipality, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select id, email, display_name, subject, role, status, language, invited_by,
                   invited_at, accepted_at, deactivated_by, deactivated_at,
                   last_sign_in_at, previous_sign_in_at, version, created_at, updated_at
              from staff_member
             where id in (:ids) and municipality_id = :tenant
            """)
        .param("ids", ids)
        .param("tenant", municipality)
        .query(StaffMemberSql::member)
        .list();
  }

  /**
   * A pertenza activa da persoa no concello (§1.2.2), co rol e os accesos: a única lectura de cada
   * petición autorizada, sobre o índice único {@code (municipality_id, subject)} das activas.
   */
  Optional<ActiveMembership> active(UUID municipality, String subject) {
    return jdbc.sql(
            """
            select id, role, last_sign_in_at, previous_sign_in_at from staff_member
             where subject = :subject and status = 'ACTIVE' and municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new ActiveMembership(
                    new StaffMemberId(rs.getObject("id", UUID.class)),
                    StaffRole.valueOf(rs.getString("role")),
                    nullableInstant(rs, "last_sign_in_at"),
                    nullableInstant(rs, "previous_sign_in_at")))
        .optional();
  }

  /**
   * Bloquea as pertenzas xestoras activas do concello e devolve cantas son: a regra do último
   * xestor (§1.2.8) decídese con elas bloqueadas, así dúas desactivacións á vez non deixan o
   * concello sen ningunha.
   */
  int lockActiveManagers(UUID municipality) {
    return jdbc.sql(
            """
            select id from staff_member
             where role = 'MANAGER' and status = 'ACTIVE' and municipality_id = :tenant
             order by id
               for update
            """)
        .param("tenant", municipality)
        .query(UUID.class)
        .list()
        .size();
  }

  /**
   * Garda unha pertenza nova coa versión 0.
   *
   * @return falso se xa hai unha pertenza non desactivada con ese correo no concello
   */
  boolean insert(UUID municipality, StaffMember m) {
    return jdbc.sql(
                """
                insert into staff_member
                  (id, municipality_id, email, display_name, subject, role, status, language,
                   invited_by, invited_at, accepted_at, deactivated_by, deactivated_at,
                   last_sign_in_at, previous_sign_in_at, created_at, updated_at, version)
                values
                  (:id, :tenant, :email, :displayName, :subject, :role, :status, :language,
                   :invitedBy, :invitedAt, :acceptedAt, :deactivatedBy, :deactivatedAt,
                   :lastSignIn, :previousSignIn, :createdAt, :updatedAt, 0)
                on conflict (municipality_id, email) where status <> 'DEACTIVATED' do nothing
                """)
            .param("id", m.id().value())
            .param("tenant", municipality)
            .param("email", m.email().value())
            .param("displayName", m.displayName(), Types.VARCHAR)
            .param("subject", m.subject(), Types.VARCHAR)
            .param("role", m.role().name())
            .param("status", m.status().name())
            .param("language", m.language().code())
            .param("invitedBy", m.invitedBy())
            .param("invitedAt", utc(m.invitedAt()))
            .param("acceptedAt", utc(m.acceptedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("deactivatedBy", m.deactivatedBy(), Types.VARCHAR)
            .param("deactivatedAt", utc(m.deactivatedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("lastSignIn", utc(m.lastSignInAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("previousSignIn", utc(m.previousSignInAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("createdAt", utc(m.createdAt()))
            .param("updatedAt", utc(m.updatedAt()))
            .update()
        == 1;
  }

  /**
   * Garda os cambios dunha pertenza (aceptar, cambiar o rol, desactivar) e sobe a versión. Os
   * accesos non: son de {@link #recordSignIn}, que non cambia a versión.
   *
   * @return falso se a versión xa non é {@code expectedVersion}: outra escritura chegou antes
   */
  boolean update(UUID municipality, StaffMember m, long expectedVersion) {
    return jdbc.sql(
                """
                update staff_member
                   set display_name = :displayName, subject = :subject, role = :role,
                       status = :status, accepted_at = :acceptedAt, deactivated_by = :deactivatedBy,
                       deactivated_at = :deactivatedAt, updated_at = :updatedAt, version = version + 1
                 where id = :id and version = :version and municipality_id = :tenant
                """)
            .param("displayName", m.displayName(), Types.VARCHAR)
            .param("subject", m.subject(), Types.VARCHAR)
            .param("role", m.role().name())
            .param("status", m.status().name())
            .param("acceptedAt", utc(m.acceptedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("deactivatedBy", m.deactivatedBy(), Types.VARCHAR)
            .param("deactivatedAt", utc(m.deactivatedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("updatedAt", utc(m.updatedAt()))
            .param("id", m.id().value())
            .param("version", expectedVersion)
            .param("tenant", municipality)
            .update()
        == 1;
  }

  /**
   * Un acceso novo da persoa (§1.2.5): o último pasa a anterior se {@code authTime} é máis novo. Só
   * unha das peticións simultáneas da mesma sesión toca a fila.
   *
   * @return 1 se este acceso é novo; 0 se xa estaba rexistrado ou a persoa non é activa
   */
  int recordSignIn(UUID municipality, String subject, Instant authTime) {
    return jdbc.sql(
            """
            update staff_member
               set previous_sign_in_at = last_sign_in_at, last_sign_in_at = :authTime
             where subject = :subject and status = 'ACTIVE'
               and (last_sign_in_at is null or last_sign_in_at < :authTime)
               and municipality_id = :tenant
            """)
        .param("authTime", utc(authTime))
        .param("subject", subject)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Anonimiza as pertenzas desactivadas antes de {@code cutoff} (§1.2.14): o correo pasa a {@code
   * anon-<id>@anonimizada.invalid} e o nome a nulo, cumprindo os checks da táboa. Idempotente: as
   * que xa teñen o correo anonimizado non cambian (nin a versión).
   *
   * @return as anonimizadas agora, para o rexistro de actividade
   */
  List<RetainedMember> anonymiseDeactivatedBefore(UUID municipality, Instant cutoff, Instant now) {
    return jdbc.sql(
            """
            update staff_member
               set email = :prefix || cast(id as text) || '@' || :domain, display_name = null,
                   updated_at = :now, version = version + 1
             where status = 'DEACTIVATED' and deactivated_at < :cutoff and email not like :anonymised
               and municipality_id = :tenant
            returning id, role
            """)
        .param("prefix", EmailAddress.ANONYMISED_PREFIX)
        .param("domain", EmailAddress.ANONYMISED_DOMAIN)
        .param("now", utc(now))
        .param("cutoff", utc(cutoff))
        .param("anonymised", "%@" + EmailAddress.ANONYMISED_DOMAIN)
        .param("tenant", municipality)
        .query(StaffMemberSql::retained)
        .list();
  }

  /**
   * Pecha as pertenzas invitadas que nunca aceptaron e xa non teñen ningunha invitación (a
   * conservación borrou a última, §1.2.14; RGPD art. 5.1.c): pasan a {@code DEACTIVATED} por {@code
   * by} en {@code now} e anonimízanse coma as desactivadas vellas (correo {@code
   * anon-<id>@anonimizada.invalid}, nome nulo). {@code invitedBefore} é unha garda: unha pertenza
   * invitada despois non pode ter xa todas as súas invitacións caducadas e borradas. Idempotente:
   * despois xa non está {@code INVITED}.
   *
   * @return as pechadas agora, para o rexistro de actividade
   */
  List<RetainedMember> deactivateUnacceptedWithoutInvitation(
      UUID municipality, Instant invitedBefore, String by, Instant now) {
    return jdbc.sql(
            """
            update staff_member
               set status = 'DEACTIVATED', deactivated_by = :by, deactivated_at = :now,
                   email = :prefix || cast(id as text) || '@' || :domain, display_name = null,
                   updated_at = :now, version = version + 1
             where status = 'INVITED' and invited_at < :invitedBefore
               and not exists (select 1 from staff_invitation i
                                where i.member_id = staff_member.id
                                  and i.municipality_id = :tenant)
               and municipality_id = :tenant
            returning id, role
            """)
        .param("by", by)
        .param("now", utc(now))
        .param("prefix", EmailAddress.ANONYMISED_PREFIX)
        .param("domain", EmailAddress.ANONYMISED_DOMAIN)
        .param("invitedBefore", utc(invitedBefore))
        .param("tenant", municipality)
        .query(StaffMemberSql::retained)
        .list();
  }

  private static RetainedMember retained(ResultSet rs, int row) throws SQLException {
    return new RetainedMember(
        new StaffMemberId(rs.getObject("id", UUID.class)), StaffRole.valueOf(rs.getString("role")));
  }

  private static StaffMember member(ResultSet rs, int row) throws SQLException {
    return StaffMember.rehydrate(
        new StaffMemberId(rs.getObject("id", UUID.class)),
        new EmailAddress(rs.getString("email")),
        rs.getString("display_name"),
        rs.getString("subject"),
        StaffRole.valueOf(rs.getString("role")),
        StaffStatus.valueOf(rs.getString("status")),
        Language.fromCode(rs.getString("language")).orElseThrow(),
        rs.getString("invited_by"),
        instant(rs, "invited_at"),
        nullableInstant(rs, "accepted_at"),
        rs.getString("deactivated_by"),
        nullableInstant(rs, "deactivated_at"),
        nullableInstant(rs, "last_sign_in_at"),
        nullableInstant(rs, "previous_sign_in_at"),
        rs.getLong("version"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
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
