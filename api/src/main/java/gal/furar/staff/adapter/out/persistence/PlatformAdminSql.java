// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.staff.application.port.out.PlatformAdminRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A administración da plataforma en SQL (ADR-0028). Táboa sen concello ({@code TenancyAllowlist}):
 * só o {@code sub} de Keycloak, cando e por que vía. A primeira administración vai nunha sentenza
 * condicional: sen ningunha administración e, polo índice único parcial de {@code furar:first-run},
 * unha soa vez.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "A administración da plataforma é de toda a instalación, non dun concello: a táboa non ten"
            + " municipality_id")
class PlatformAdminSql implements PlatformAdminRepository {

  /** Conta de sistema coa que queda gardada a primeira administración. */
  static final String FIRST_RUN = "furar:first-run";

  private final JdbcClient jdbc;

  PlatformAdminSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isAdmin(String subject) {
    return Boolean.TRUE.equals(
        jdbc.sql("select exists (select 1 from platform_admin where subject = :subject)")
            .param("subject", subject)
            .query(Boolean.class)
            .single());
  }

  @Override
  public boolean grantFirst(UUID id, String subject, Instant at) {
    return jdbc.sql(
                """
                insert into platform_admin (id, subject, granted_at, granted_by)
                select :id, :subject, :at, :grantedBy
                 where not exists (select 1 from platform_admin)
                on conflict do nothing
                """)
            .param("id", id)
            .param("subject", subject)
            .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
            .param("grantedBy", FIRST_RUN)
            .update()
        == 1;
  }
}
