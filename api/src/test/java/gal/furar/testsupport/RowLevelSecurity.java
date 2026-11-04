// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import gal.furar.shared.tenancy.TenancyAllowlist;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A regra de RLS de D14 (ADR-0006), nun só sitio: a usan {@code SchemaTenancyIT} e {@code
 * NativeSqlRowLevelSecurityIT}. Unha táboa do concello cumpre se ten RLS activa, polo menos unha
 * política {@code PERMISSIVE FOR ALL} cuxo {@code USING} (e {@code WITH CHECK}, se o hai) compara
 * {@code municipality_id} con {@code furar.municipality_id}, e ningunha outra política permisiva
 * sen ese filtro (as permisivas súmanse con OR: unha {@code using (true)} ou {@code using
 * (municipality_id is not null)} abriría a táboa).
 */
public final class RowLevelSecurity {

  private RowLevelSecurity() {}

  /** Unha política tal como a devolve {@code pg_policies}. */
  public record Policy(String name, String permissive, String cmd, String qual, String withCheck) {}

  /** Problemas da táboa no esquema real; baleiro se cumpre. */
  public static List<String> problems(JdbcClient jdbc, String table) {
    Boolean enabled =
        jdbc.sql(
                """
                select coalesce(bool_or(c.relrowsecurity), false) from pg_class c
                join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
                where c.relname = :t and c.relkind = 'r'
                """)
            .param("t", table)
            .query(Boolean.class)
            .single();
    if (!enabled) {
      return List.of(table + ": row level security not enabled");
    }
    List<Policy> policies =
        jdbc.sql(
                """
                select policyname, permissive, cmd, coalesce(qual, '') as qual,
                       coalesce(with_check, '') as with_check
                from pg_policies where schemaname = 'public' and tablename = :t
                """)
            .param("t", table)
            .query(
                (rs, n) ->
                    new Policy(
                        rs.getString("policyname"),
                        rs.getString("permissive"),
                        rs.getString("cmd"),
                        rs.getString("qual"),
                        rs.getString("with_check")))
            .list();
    return policyProblems(table, policies);
  }

  /** A parte da regra que só mira as políticas (proba unitaria sen base de datos). */
  public static List<String> policyProblems(String table, List<Policy> policies) {
    List<String> problems = new ArrayList<>();
    boolean tenantPolicy = false;
    for (Policy p : policies) {
      boolean filters = scoped(p.qual()) && (p.withCheck().isEmpty() || scoped(p.withCheck()));
      boolean permissive = "PERMISSIVE".equals(p.permissive());
      if (filters && permissive && "ALL".equals(p.cmd())) {
        tenantPolicy = true;
      } else if (permissive && !filters) {
        problems.add(
            table + ": permissive policy " + p.name() + " does not filter by municipality");
      }
    }
    if (!tenantPolicy) {
      problems.add(table + ": no policy on municipality_id = furar.municipality_id for ALL");
    }
    return problems;
  }

  private static boolean scoped(String expression) {
    return expression.contains("municipality_id")
        && expression.contains("furar.municipality_id")
        && expression.contains("=");
  }

  /**
   * Táboas do concello no esquema real: as que teñen columna {@code municipality_id}, fóra de
   * {@link TenancyAllowlist#TABLES}.
   */
  public static List<String> municipalityTables(JdbcClient jdbc) {
    return jdbc
        .sql(
            """
            select c.table_name from information_schema.columns c
            join information_schema.tables t
              on t.table_schema = c.table_schema and t.table_name = c.table_name
            where c.table_schema = 'public' and c.column_name = 'municipality_id'
              and t.table_type = 'BASE TABLE'
            order by c.table_name
            """)
        .query(String.class)
        .list()
        .stream()
        .filter(t -> !TenancyAllowlist.TABLES.contains(t))
        .toList();
  }
}
