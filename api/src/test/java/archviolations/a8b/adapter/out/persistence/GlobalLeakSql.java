// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

/** GLOBAL sen motivo e sobre unha táboa con concello. */
@NativeSql(scope = NativeSql.Scope.GLOBAL)
public class GlobalLeakSql {
  private final JdbcClient jdbc;

  public GlobalLeakSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count() {
    return jdbc.sql("select count(*) from municipality m join custom_field d on true")
        .query(Integer.class)
        .single();
  }
}
