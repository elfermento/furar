// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** QA Q11: o predicado do concello na lista do SELECT non filtra filas. */
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
public class SelectListSql {
  private final JdbcClient jdbc;

  public SelectListSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> codes(UUID m) {
    return jdbc.sql("select municipality_id = :m as own, code from custom_field")
        .param("m", m)
        .query(String.class)
        .list();
  }
}
