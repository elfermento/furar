// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * MUNICIPALITY sen `UnprovenTenantSqlTenantIT`, cunha sentenza sen concello, un INSERT sen a
 * columna do concello e un {@code or 1=1} que anula o predicado.
 */
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
public class UnprovenTenantSql {
  private final JdbcClient jdbc;

  public UnprovenTenantSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count(UUID municipality) {
    return jdbc.sql("select count(*) from custom_field where municipality_id = :m")
        .param("m", municipality)
        .query(Integer.class)
        .single();
  }

  public int countAll() {
    return jdbc.sql("select count(*) from custom_field").query(Integer.class).single();
  }

  public int countAllSneaky(UUID municipality) {
    return jdbc.sql("select count(*) from seat_slot where municipality_id = :m or 1=1")
        .param("m", municipality)
        .query(Integer.class)
        .single();
  }

  public int countCommentedPredicate() {
    return jdbc.sql(
            """
            -- lectura dun concello
            select count(*) from seat_slot /* where municipality_id = :m */
            """)
        .query(Integer.class)
        .single();
  }

  public void insertWithoutTenant(UUID leaf) {
    jdbc.sql("insert into seat_slot (leaf_space_id, slot_start, capacity) values (:l, now(), 1)")
        .param("l", leaf)
        .update();
  }
}
