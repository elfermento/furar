// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.out.persistence;

import gal.furar.opendata.application.port.out.OpenDataStore.DatasetState;
import gal.furar.opendata.domain.Dataset;
import gal.furar.shared.persistence.NativeSql;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O estado de cada conxunto en SQL (ADR-0034, punto 6): a pegada, o día do último cambio e, na
 * ocupación, o último día calculado. Sempre dentro dunha transacción (RLS) e co concello como
 * parámetro (A8b, {@code OpenDataDatasetSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class OpenDataDatasetSql {

  private final JdbcClient jdbc;

  OpenDataDatasetSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Map<Dataset, DatasetState> all(UUID municipality) {
    Map<Dataset, DatasetState> result = new EnumMap<>(Dataset.class);
    jdbc.sql(
            """
            select dataset, checksum, changed_on, calculated_through
            from open_data_dataset
            where municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .query(
            rs -> {
              result.put(
                  Dataset.valueOf(rs.getString("dataset")),
                  new DatasetState(
                      rs.getString("checksum"),
                      rs.getObject("changed_on", LocalDate.class),
                      rs.getObject("calculated_through", LocalDate.class)));
            });
    return result;
  }

  /**
   * Alta ou cambio en dúas sentenzas (A8b le mellor un insert e un update ca un «on conflict do
   * update»).
   */
  void save(UUID municipality, Dataset dataset, DatasetState state) {
    int inserted =
        jdbc.sql(
                """
                insert into open_data_dataset
                  (municipality_id, dataset, checksum, changed_on, calculated_through)
                values (:tenant, :dataset, :checksum, :changedOn, :through)
                on conflict do nothing
                """)
            .param("tenant", municipality)
            .param("dataset", dataset.name())
            .param("checksum", state.checksum())
            .param("changedOn", state.changedOn())
            .param("through", state.calculatedThrough())
            .update();
    if (inserted == 0) {
      jdbc.sql(
              """
              update open_data_dataset
              set checksum = :checksum, changed_on = :changedOn, calculated_through = :through
              where dataset = :dataset and municipality_id = :tenant
              """)
          .param("checksum", state.checksum())
          .param("changedOn", state.changedOn())
          .param("through", state.calculatedThrough())
          .param("dataset", dataset.name())
          .param("tenant", municipality)
          .update();
    }
  }

  void deleteAll(UUID municipality) {
    jdbc.sql(
            """
            delete from open_data_dataset
            where municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .update();
  }
}
