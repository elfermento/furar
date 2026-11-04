// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.staff.application.port.out.StaffAccountLookup;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Se unha persoa é persoal activo nalgún concello: chama á función {@code is_active_staff_account}
 * (V202610301000), que se executa como o dono porque a RLS de {@code staff_member} só deixa ver o
 * concello da transacción, e só devolve si ou non. Sen táboas no SQL.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "Unha conta de traballo é de traballo en todos os concellos; a función só di se a persoa"
            + " é persoal activo nalgún, sen o concello nin o rol")
class StaffAccountSql implements StaffAccountLookup {

  private final JdbcClient jdbc;

  StaffAccountSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isActiveAnywhere(String subject) {
    return Boolean.TRUE.equals(
        jdbc.sql("select is_active_staff_account(:subject)")
            .param("subject", subject)
            .query(Boolean.class)
            .single());
  }
}
