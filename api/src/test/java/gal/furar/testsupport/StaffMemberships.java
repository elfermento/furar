// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import gal.furar.shared.security.RoleGrant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Pertenzas ACTIVE para os tokens de proba con rol de persoal ({@link TestTokens#manager}): o rol
 * do persoal só sae da táboa {@code staff_member} (ADR-0027), así que o token leva o {@code sub} e
 * a fila dálle o rol. Escríbese como dono da base (sen RLS), no concello co slug do rol; se aínda
 * non existe, non se escribe nada e o token non ten rol. A administración da plataforma ({@link
 * TestTokens#platformAdmin}), igual, en {@code platform_admin} (ADR-0028).
 */
final class StaffMemberships {

  private static final JdbcClient OWNER =
      JdbcClient.create(
          new SimpleDriverDataSource(
              new org.postgresql.Driver(),
              PostgresContainers.shared().getJdbcUrl(),
              PostgresContainers.OWNER_USER,
              PostgresContainers.OWNER_PASSWORD));

  private StaffMemberships() {}

  static void activate(String subject, RoleGrant grant) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OWNER
        .sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at,
               version)
            select :id, m.id, :email, 'Persoa de proba', :subject, :role, 'ACTIVE', 'gl',
                   'test-support', :now, :now, :now, :now, 0
            from municipality m
            where m.slug = :slug
            on conflict do nothing
            """)
        .param("id", id)
        .param("email", "persoal-" + id + "@example.org")
        .param("subject", subject)
        .param("role", grant.role().name())
        .param("now", now)
        .param("slug", grant.scope())
        .update();
  }

  /** Fai do {@code sub} administración da plataforma (ADR-0028). */
  static void platformAdmin(String subject) {
    OWNER
        .sql(
            """
            insert into platform_admin (id, subject, granted_at, granted_by)
            values (:id, :subject, :now, 'test-support')
            on conflict do nothing
            """)
        .param("id", UUID.randomUUID())
        .param("subject", subject)
        .param("now", OffsetDateTime.now(ZoneOffset.UTC))
        .update();
  }
}
