// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Comproba xa, e non no commit, a restrición diferida dos nomes das partes ({@code
 * facility_use_part_name_ex}, migración {@code facility_uses}). É diferida para que un gardado
 * poida intercambiar os nomes de dúas partes (§1.2.15 do plan); se esperase ao commit, o erro
 * sairía fóra do adaptador e non se podería traducir a {@code facility.use-part-name-taken}. Volve
 * deixala diferida despois.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "SET CONSTRAINTS só cambia cando se comproba unha restrición; non le nin escribe filas")
class UsePartNameCheck {

  private final JdbcClient jdbc;

  UsePartNameCheck(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Lanza o erro de integridade se hai dous nomes iguais entre as partes vivas dun uso. */
  void checkNow() {
    jdbc.sql("set constraints facility_use_part_name_ex immediate").update();
    jdbc.sql("set constraints facility_use_part_name_ex deferred").update();
  }
}
