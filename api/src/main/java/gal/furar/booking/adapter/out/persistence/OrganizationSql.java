// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.season.Organization;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As entidades e os seus responsables en SQL (ADR-0039, punto 7). Sempre dentro dunha transacción
 * (RLS) e co concello como parámetro (A8b, {@code OrganizationSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class OrganizationSql {

  private final JdbcClient jdbc;

  OrganizationSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Unha fila sen os responsables. */
  record Head(
      UUID id,
      String name,
      @Nullable String contactName,
      @Nullable String email,
      @Nullable String phone,
      @Nullable Instant archivedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  /** Unha fila da táboa, co reconto e o estado. */
  record Listed(Head head, int managers, boolean pending, boolean future) {}

  Optional<Head> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, name, contact_name, email, phone, archived_at, version, created_at,
                   updated_at
            from booking_organization
            where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> head(rs))
        .optional();
  }

  Optional<Head> findForUpdate(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, name, contact_name, email, phone, archived_at, version, created_at,
                   updated_at
            from booking_organization
            where id = :id and municipality_id = :tenant
            for update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> head(rs))
        .optional();
  }

  Optional<Head> findForShare(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, name, contact_name, email, phone, archived_at, version, created_at,
                   updated_at
            from booking_organization
            where id = :id and municipality_id = :tenant
            for share
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> head(rs))
        .optional();
  }

  /** Os responsables destas entidades, por orde de alta. */
  List<ManagerRow> managers(UUID municipality, Collection<UUID> organizations) {
    if (organizations.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select organization_id, email_key, email, added_at, added_by
            from booking_organization_manager
            where organization_id in (:organizations) and municipality_id = :tenant
            order by added_at, email_key
            """)
        .param("organizations", List.copyOf(organizations))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new ManagerRow(
                    rs.getObject("organization_id", UUID.class),
                    new Organization.Manager(
                        rs.getString("email_key"),
                        rs.getString("email"),
                        BookingSql.instant(rs, "added_at"),
                        rs.getString("added_by"))))
        .list();
  }

  /** Un responsable coa súa entidade. */
  record ManagerRow(UUID organizationId, Organization.Manager manager) {}

  /**
   * As entidades (dadas de alta ou arquivadas), por nome, con cantos responsables teñen, se teñen
   * algunha solicitude pendente e se teñen algunha sesión confirmada que aínda non rematou.
   */
  List<Listed> list(UUID municipality, boolean archived, Instant now) {
    return jdbc.sql(
            """
            select o.id, o.name, o.contact_name, o.email, o.phone, o.archived_at, o.version,
                   o.created_at, o.updated_at,
                   (select count(*) from booking_organization_manager m
                     where m.organization_id = o.id and m.municipality_id = :tenant) as managers,
                   exists (select 1 from booking_series_request r
                            where r.organization_id = o.id and r.status = 'PENDING'
                              and r.municipality_id = :tenant) as pending,
                   exists (select 1 from booking b
                            where b.organization_id = o.id and b.series_id is not null
                              and b.status = 'CONFIRMED' and upper(b.period) > :now
                              and b.municipality_id = :tenant) as future
            from booking_organization o
            where (o.archived_at is not null) = :archived
              and o.municipality_id = :tenant
            order by lower(o.name), o.id
            """)
        .param("archived", archived)
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new Listed(
                    head(rs),
                    rs.getInt("managers"),
                    rs.getBoolean("pending"),
                    rs.getBoolean("future")))
        .list();
  }

  void insert(UUID municipality, Organization o) {
    jdbc.sql(
            """
            insert into booking_organization
              (id, municipality_id, name, contact_name, email, phone, archived_at, created_at,
               updated_at, version)
            values (:id, :tenant, :name, :contact, :email, :phone, null, :createdAt, :updatedAt, 0)
            """)
        .param("id", o.id())
        .param("tenant", municipality)
        .param("name", o.name())
        .param("contact", o.contactName(), Types.VARCHAR)
        .param("email", o.email(), Types.VARCHAR)
        .param("phone", o.phone(), Types.VARCHAR)
        .param("createdAt", BookingSql.utc(o.createdAt()))
        .param("updatedAt", BookingSql.utc(o.updatedAt()))
        .update();
  }

  /**
   * @return falso se a versión xa non é {@code expected}
   */
  boolean update(UUID municipality, Organization o, long expected) {
    return jdbc.sql(
                """
                update booking_organization
                set name = :name, contact_name = :contact, email = :email, phone = :phone,
                    archived_at = :archivedAt, updated_at = :updatedAt, version = version + 1
                where id = :id and version = :version and municipality_id = :tenant
                """)
            .param("name", o.name())
            .param("contact", o.contactName(), Types.VARCHAR)
            .param("email", o.email(), Types.VARCHAR)
            .param("phone", o.phone(), Types.VARCHAR)
            .param(
                "archivedAt",
                o.archivedAt() == null ? null : BookingSql.utc(o.archivedAt()),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .param("updatedAt", BookingSql.utc(o.updatedAt()))
            .param("id", o.id())
            .param("version", expected)
            .param("tenant", municipality)
            .update()
        == 1;
  }

  int deleteManagers(UUID municipality, UUID organization) {
    return jdbc.sql(
            """
            delete from booking_organization_manager
            where organization_id = :organization and municipality_id = :tenant
            """)
        .param("organization", organization)
        .param("tenant", municipality)
        .update();
  }

  void insertManager(UUID municipality, UUID organization, Organization.Manager m) {
    jdbc.sql(
            """
            insert into booking_organization_manager
              (municipality_id, organization_id, email_key, email, added_at, added_by)
            values (:tenant, :organization, :key, :email, :addedAt, :addedBy)
            """)
        .param("tenant", municipality)
        .param("organization", organization)
        .param("key", m.emailKey())
        .param("email", m.email())
        .param("addedAt", BookingSql.utc(m.addedAt()))
        .param("addedBy", m.addedBy())
        .update();
  }

  int delete(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            delete from booking_organization
            where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .update();
  }

  boolean hasBookings(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select 1 from booking
            where organization_id = :organization and municipality_id = :tenant
            limit 1
            """)
        .param("organization", id)
        .param("tenant", municipality)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  boolean hasSeries(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select 1 from booking_series
            where organization_id = :organization and municipality_id = :tenant
            limit 1
            """)
        .param("organization", id)
        .param("tenant", municipality)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  boolean hasFutureBookings(UUID municipality, UUID id, Instant now) {
    return jdbc.sql(
            """
            select 1 from booking
            where organization_id = :organization and status = 'CONFIRMED'
              and upper(period) > :now
              and municipality_id = :tenant
            limit 1
            """)
        .param("organization", id)
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  /** As entidades dadas de alta dese correo, por nome. */
  List<UUID> ofManager(UUID municipality, String emailKey) {
    return jdbc.sql(
            """
            select m.organization_id
            from booking_organization_manager m
            join booking_organization o
              on o.id = m.organization_id and o.municipality_id = m.municipality_id
            where m.email_key = :key and o.archived_at is null
              and m.municipality_id = :tenant
            order by lower(o.name), o.id
            """)
        .param("key", emailKey)
        .param("tenant", municipality)
        .query(UUID.class)
        .list();
  }

  int renameBookings(UUID municipality, UUID id, String name) {
    return jdbc.sql(
            """
            update booking set holder_name = :name
            where organization_id = :organization and anonymized_at is null
              and holder_name <> :name
              and municipality_id = :tenant
            """)
        .param("name", name)
        .param("organization", id)
        .param("tenant", municipality)
        .update();
  }

  List<Head> byIds(UUID municipality, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select id, name, contact_name, email, phone, archived_at, version, created_at,
                   updated_at
            from booking_organization
            where id in (:ids) and municipality_id = :tenant
            """)
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .query((rs, n) -> head(rs))
        .list();
  }

  private static Head head(ResultSet rs) throws SQLException {
    OffsetDateTime archived = rs.getObject("archived_at", OffsetDateTime.class);
    return new Head(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("contact_name"),
        rs.getString("email"),
        rs.getString("phone"),
        archived == null ? null : archived.toInstant(),
        rs.getLong("version"),
        BookingSql.instant(rs, "created_at"),
        BookingSql.instant(rs, "updated_at"));
  }
}
