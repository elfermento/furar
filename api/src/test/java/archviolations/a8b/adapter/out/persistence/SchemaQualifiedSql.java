// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

/** QA Q12: co esquema diante, a regex antiga capturaba «public» e non a táboa. */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "fixture")
public class SchemaQualifiedSql {
  private final JdbcClient jdbc;

  public SchemaQualifiedSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count() {
    return jdbc.sql("select count(*) from public.custom_field").query(Integer.class).single();
  }
}
