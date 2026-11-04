// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8bok.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * MUNICIPALITY correcta: concello como parámetro en cada sentenza (no INSERT, na lista de columnas;
 * fóra de parénteses; un {@code or} dentro de parénteses vale se o predicado do concello vai fóra)
 * e o seu TenantIT. É a forma do SQL de booking (ADR-0012).
 */
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
public class ScopedSql {
  private final JdbcClient jdbc;

  public ScopedSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int count(UUID municipality) {
    return jdbc.sql(
            """
            -- só as activas
            select count(*) from custom_field
            where municipality_id = :municipality and archived_at is null
            """)
        .param("municipality", municipality)
        .query(Integer.class)
        .single();
  }

  public int countIn(java.util.List<UUID> municipalities) {
    return jdbc.sql("select count(*) from seat_slot where municipality_id in (:ms)")
        .param("ms", municipalities)
        .query(Integer.class)
        .single();
  }

  public void take(UUID municipality, UUID leaf, int seats) {
    jdbc.sql(
            """
            insert into seat_slot (leaf_space_id, slot_start, municipality_id, capacity)
            values (:leaf, now(), :municipality, 30) on conflict do nothing
            """)
        .param("leaf", leaf)
        .param("municipality", municipality)
        .update();
    jdbc.sql(
            """
            update seat_slot set taken = taken + :seats
            where leaf_space_id = :leaf and municipality_id = :municipality
              and (taken = 0 or capacity > 1)
            """)
        .param("seats", seats)
        .param("leaf", leaf)
        .param("municipality", municipality)
        .update();
  }
}
