// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

/** QA Q12: a táboa do concello vai despois dunha coma no FROM. */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "fixture")
public class CommaJoinSql {
  private final JdbcClient jdbc;

  public CommaJoinSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count() {
    return jdbc.sql("select count(*) from municipality m, custom_field d where true")
        .query(Integer.class)
        .single();
  }
}
