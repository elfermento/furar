// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T11: GLOBAL cun motivo, pero o SQL constrúese en execución a partir de anacos. Os literais
 * ({@code "select"}, {@code " municipality_id from custom_field"}) non son unha sentenza enteira,
 * así que a comprobación de táboas non vería a fuga.
 */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "proba")
public class SplitSql {
  private final JdbcClient jdbc;

  public SplitSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public UUID any() {
    return jdbc.sql("select".trim() + " municipality_id from custom_field limit 1")
        .query(UUID.class)
        .single();
  }
}
