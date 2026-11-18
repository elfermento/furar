// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * QA1: GLOBAL con motivo, pero un comentario diante agocha unha lectura entre concellos; e dollar
 * quoting, que A8b non sabe ler.
 */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "fixture")
public class CommentedGlobalSql {
  private final JdbcClient jdbc;

  public CommentedGlobalSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count() {
    return jdbc.sql("/* qa */ select count(*) from custom_field").query(Integer.class).single();
  }

  public int dollar() {
    return jdbc.sql("select $$ -- $$, count(*) from custom_field").query(Integer.class).single();
  }
}
