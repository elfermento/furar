// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

/** QN1: `TABLE t`, `from (table t)` e SQL dinámico nun texto len todas as filas. */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "fixture")
public class TableStatementSql {
  private final JdbcClient jdbc;

  public TableStatementSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Object all() {
    return jdbc.sql("table custom_field").query().listOfRows();
  }

  public Object nested() {
    return jdbc.sql("select count(*) from (table custom_field) t").query().listOfRows();
  }

  public Object dynamic() {
    return jdbc.sql("select query_to_xml('select * from custom_field', true, false, '')")
        .query()
        .listOfRows();
  }
}
