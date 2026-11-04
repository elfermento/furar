// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * QA Q3: o predicado do concello só dentro dun {@code or} entre parénteses, despois de {@code not},
 * nun {@code union} ou nun {@code case}; e un TenantIT baleiro (sen {@code @Test} nin AbstractIT).
 */
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
public class ParenOrSql {
  private final JdbcClient jdbc;

  public ParenOrSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public int parenOr(UUID m) {
    return jdbc.sql("select count(*) from seat_slot where (municipality_id = :m or 1 = 1)")
        .param("m", m)
        .query(Integer.class)
        .single();
  }

  public int negated(UUID m) {
    return jdbc.sql("select count(*) from seat_slot where not municipality_id = :m")
        .param("m", m)
        .query(Integer.class)
        .single();
  }

  public int union(UUID m) {
    return jdbc.sql(
            "select capacity from seat_slot where municipality_id = :m"
                + " union select capacity from seat_slot")
        .param("m", m)
        .query(Integer.class)
        .single();
  }

  public int caseWhen(UUID m) {
    return jdbc.sql(
            "select count(*) from seat_slot where municipality_id = :m"
                + " and case when true then true end")
        .param("m", m)
        .query(Integer.class)
        .single();
  }
}
