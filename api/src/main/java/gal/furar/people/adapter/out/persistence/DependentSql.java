// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.out.persistence;

import gal.furar.people.application.port.out.DependentRepository;
import gal.furar.people.domain.Dependent;
import gal.furar.people.domain.DependentId;
import gal.furar.people.domain.PeopleError;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As persoas a cargo en SQL (ADR-0031, punto 2). Táboa sen concello ({@code TenancyAllowlist}):
 * toda sentenza leva {@code account_subject = :subject}, agás as dúas da conservación, que
 * percorren as desactivadas de todas as contas.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "As persoas a cargo son da conta, común a todos os concellos: a táboa non ten"
            + " municipality_id e cada sentenza vai filtrada polo sub da conta")
class DependentSql implements DependentRepository {

  private final JdbcClient jdbc;

  DependentSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Dependent> active(String subject) {
    return jdbc.sql(
            """
            select id, account_subject, name, birth_year, declared_at, deactivated_at, version,
                   created_at, updated_at
            from account_dependent
            where account_subject = :subject and deactivated_at is null
            order by lower(name), birth_year, id
            """)
        .param("subject", subject)
        .query((rs, n) -> dependent(rs))
        .list();
  }

  @Override
  public List<Dependent> active(String subject, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select id, account_subject, name, birth_year, declared_at, deactivated_at, version,
                   created_at, updated_at
            from account_dependent
            where account_subject = :subject and deactivated_at is null and id in (:ids)
            order by lower(name), birth_year, id
            """)
        .param("subject", subject)
        .param("ids", List.copyOf(ids))
        .query((rs, n) -> dependent(rs))
        .list();
  }

  @Override
  public List<Dependent> lockActive(String subject) {
    return jdbc.sql(
            """
            select id, account_subject, name, birth_year, declared_at, deactivated_at, version,
                   created_at, updated_at
            from account_dependent
            where account_subject = :subject and deactivated_at is null
            order by id
            for update
            """)
        .param("subject", subject)
        .query((rs, n) -> dependent(rs))
        .list();
  }

  @Override
  public Optional<Dependent> lock(String subject, DependentId id) {
    return jdbc.sql(
            """
            select id, account_subject, name, birth_year, declared_at, deactivated_at, version,
                   created_at, updated_at
            from account_dependent
            where account_subject = :subject and id = :id
            for update
            """)
        .param("subject", subject)
        .param("id", id.value())
        .query((rs, n) -> dependent(rs))
        .optional();
  }

  @Override
  public void insert(Dependent d) {
    try {
      jdbc.sql(
              """
              insert into account_dependent
                (id, account_subject, name, birth_year, declared_at, created_at, updated_at,
                 version)
              values (:id, :subject, :name, :birthYear, :declaredAt, :createdAt, :updatedAt, 0)
              """)
          .param("id", d.id().value())
          .param("subject", d.accountSubject())
          .param("name", d.name())
          .param("birthYear", d.birthYear())
          .param("declaredAt", utc(d.declaredAt()))
          .param("createdAt", utc(d.createdAt()))
          .param("updatedAt", utc(d.updatedAt()))
          .update();
    } catch (DuplicateKeyException e) {
      throw new DomainException(PeopleError.DEPENDENT_DUPLICATE);
    }
  }

  @Override
  public void update(Dependent d) {
    int updated;
    try {
      updated =
          jdbc.sql(
                  """
                  update account_dependent
                  set name = :name, birth_year = :birthYear, declared_at = :declaredAt,
                      deactivated_at = :deactivatedAt, updated_at = :updatedAt,
                      version = version + 1
                  where account_subject = :subject and id = :id and version = :version
                  """)
              .param("name", d.name())
              .param("birthYear", d.birthYear())
              .param("declaredAt", utc(d.declaredAt()))
              .param(
                  "deactivatedAt",
                  d.deactivatedAt() == null ? null : utc(d.deactivatedAt()),
                  Types.TIMESTAMP_WITH_TIMEZONE)
              .param("updatedAt", utc(d.updatedAt()))
              .param("subject", d.accountSubject())
              .param("id", d.id().value())
              .param("version", Objects.requireNonNull(d.version(), "version"))
              .update();
    } catch (DuplicateKeyException e) {
      throw new DomainException(PeopleError.DEPENDENT_DUPLICATE);
    }
    if (updated != 1) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
  }

  @Override
  public void delete(String subject, DependentId id) {
    jdbc.sql("delete from account_dependent where account_subject = :subject and id = :id")
        .param("subject", subject)
        .param("id", id.value())
        .update();
  }

  @Override
  public List<UUID> deactivated() {
    return jdbc.sql("select id from account_dependent where deactivated_at is not null order by id")
        .query(UUID.class)
        .list();
  }

  @Override
  public void purge(UUID id) {
    jdbc.sql("delete from account_dependent where id = :id and deactivated_at is not null")
        .param("id", id)
        .update();
  }

  private static Dependent dependent(ResultSet rs) throws SQLException {
    return Dependent.rehydrate(
        new DependentId(rs.getObject("id", UUID.class)),
        rs.getString("account_subject"),
        rs.getString("name"),
        rs.getInt("birth_year"),
        instant(rs, "declared_at"),
        instant(rs, "deactivated_at"),
        rs.getLong("version"),
        Objects.requireNonNull(instant(rs, "created_at")),
        Objects.requireNonNull(instant(rs, "updated_at")));
  }

  private static OffsetDateTime utc(Instant at) {
    return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
  }

  private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
