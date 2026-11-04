// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantRef;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Os concellos por JDBC, para as tarefas da plataforma que traballan en cada un. */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason = "municipality non leva municipality_id: a tarefa da plataforma percorre todos")
class MunicipalityDirectoryJdbc implements MunicipalityDirectory {

  private final JdbcClient jdbc;

  MunicipalityDirectoryJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<TenantRef> all() {
    return jdbc.sql("select id, slug from municipality order by id")
        .query((rs, row) -> new TenantRef(rs.getObject("id", UUID.class), rs.getString("slug")))
        .list();
  }
}
