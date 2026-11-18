// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.out.persistence;

import gal.furar.people.application.port.out.AccountEmails;
import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As contas coñecidas polo seu correo en SQL (ADR-0031, punto 4). Táboa sen concello ({@code
 * TenancyAllowlist}): só o {@code sub}, o correo normalizado e cando se viu.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "A conta é común a todos os concellos: a táboa non ten municipality_id; só garda o sub e"
            + " o correo verificado que xa chegan no token")
class AccountEmailSql implements AccountEmails {

  private final JdbcClient jdbc;

  AccountEmailSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Seen> find(String subject) {
    return jdbc.sql("select email, seen_at from account_email where account_subject = :subject")
        .param("subject", subject)
        .query(
            (rs, n) ->
                new Seen(
                    rs.getString("email"),
                    rs.getObject("seen_at", OffsetDateTime.class).toInstant()))
        .optional();
  }

  @Override
  public void write(String subject, String emailKey, Instant seenAt) {
    jdbc.sql("delete from account_email where email = :email and account_subject <> :subject")
        .param("email", emailKey)
        .param("subject", subject)
        .update();
    OffsetDateTime at = OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC);
    // En dúas sentenzas (A8b le mellor un update e un insert ca un «on conflict do update»): a
    // fila que xa existe só cambia se a sesión é máis nova; a que non existe, insírese.
    jdbc.sql(
            """
            update account_email set email = :email, seen_at = :seenAt
            where account_subject = :subject and seen_at < :seenAt
            """)
        .param("subject", subject)
        .param("email", emailKey)
        .param("seenAt", at)
        .update();
    jdbc.sql(
            """
            insert into account_email (account_subject, email, seen_at)
            values (:subject, :email, :seenAt)
            on conflict (account_subject) do nothing
            """)
        .param("subject", subject)
        .param("email", emailKey)
        .param("seenAt", at)
        .update();
  }

  @Override
  public Optional<String> subjectOf(String emailKey) {
    return jdbc.sql("select account_subject from account_email where email = :email")
        .param("email", emailKey)
        .query(String.class)
        .optional();
  }

  @Override
  public int purgeSeenBefore(Instant before) {
    return jdbc.sql("delete from account_email where seen_at < :before")
        .param("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC))
        .update();
  }
}
