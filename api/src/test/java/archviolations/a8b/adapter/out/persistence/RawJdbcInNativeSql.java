// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * QA2 dentro de @NativeSql: o literal parece correcto, pero vai por JDBC cru, que A8b non le como
 * sentenza; ten que fallar igual.
 */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "fixture")
public class RawJdbcInNativeSql {
  private final DataSource dataSource;

  public RawJdbcInNativeSql(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public int count() throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("select count(*) from municipality")) {
      r.next();
      return r.getInt(1);
    }
  }
}
