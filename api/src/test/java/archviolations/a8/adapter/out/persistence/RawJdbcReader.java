// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8.adapter.out.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/** QA2: JDBC cru (DataSource → Statement) nunha clase sen @NativeSql. */
public class RawJdbcReader {
  private final DataSource dataSource;

  public RawJdbcReader(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public int count() throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("select count(*) from custom_field")) {
      r.next();
      return r.getInt(1);
    }
  }
}
