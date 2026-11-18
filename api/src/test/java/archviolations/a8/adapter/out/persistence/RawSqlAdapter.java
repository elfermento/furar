// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8.adapter.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

public class RawSqlAdapter {
  private final JdbcClient jdbc;

  public RawSqlAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count() {
    return jdbc.sql("select count(*) from custom_field").query(Integer.class).single();
  }
}
