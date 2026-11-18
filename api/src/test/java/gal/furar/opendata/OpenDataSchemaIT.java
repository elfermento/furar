// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * As táboas dos datos abertos non teñen datos de quen reserva por construción (ADR-0034, punto 9):
 * ningunha columna de persoa, reserva, conta, correo, nome nin texto libre.
 */
class OpenDataSchemaIT extends AbstractIT {

  @Test
  void openDataTablesHaveNoPersonalColumns() {
    List<String> columns =
        jdbc.sql(
                """
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'public' and table_name like 'open\\_data\\_%'
                order by 1
                """)
            .query(String.class)
            .list();
    assertThat(columns)
        .containsExactlyInAnyOrder(
            "open_data_dataset.calculated_through",
            "open_data_dataset.changed_on",
            "open_data_dataset.checksum",
            "open_data_dataset.dataset",
            "open_data_dataset.municipality_id",
            "open_data_occupancy.day",
            "open_data_occupancy.facility_id",
            "open_data_occupancy.municipality_id",
            "open_data_occupancy.quarter_uses",
            "open_data_occupancy.quarters",
            "open_data_occupancy.uses");
    assertThat(columns)
        .noneMatch(
            c -> c.matches(".*\\.(.*(holder|person|account|email|name|subject|booking|reason).*)"));
  }
}
