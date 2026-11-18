// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.platform;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import org.junit.jupiter.api.Test;

/** D13: os IT corren contra PostgreSQL 18.6 real, non contra unha imitación. */
class PostgresContainerIT extends AbstractIT {

  @Test
  void runsOnPostgres186() {
    String version = jdbc.sql("select version()").query(String.class).single();
    assertThat(version).startsWith("PostgreSQL 18.6");
  }

  @Test
  void hasBtreeGist() {
    Integer count =
        jdbc.sql("select count(*) from pg_extension where extname = 'btree_gist'")
            .query(Integer.class)
            .single();
    assertThat(count).isEqualTo(1);
  }
}
