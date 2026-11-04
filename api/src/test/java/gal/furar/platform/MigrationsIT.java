// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.platform;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.MigrationLock;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D13: Flyway aplicou as migracións desde cero ao arrincar. Se faltase {@code
 * spring-boot-starter-flyway}, a táboa de historial estaría baleira (ADR-0004).
 */
class MigrationsIT extends AbstractIT {

  @Autowired Flyway flyway;

  @Test
  void historyIsNotEmptyAndEverythingIsApplied() {
    Integer applied =
        jdbc.sql("select count(*) from flyway_schema_history where success")
            .query(Integer.class)
            .single();
    assertThat(applied).isGreaterThanOrEqualTo(2);
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
  }

  /**
   * O aplicado desde cero é exactamente o rexistrado en {@code migrations.lock}, e ningunha
   * migración rexistrada cambiou (QA8; MigrationLockTest proba as violacións).
   */
  @Test
  void appliedMigrationsMatchTheLock() {
    assertThat(MigrationLock.problems(MigrationLock.DIR, MigrationLock.LOCK)).isEmpty();
    assertThat(flyway.info().applied())
        .filteredOn(info -> info.getVersion() != null)
        .extracting(MigrationInfo::getScript)
        .containsExactlyElementsOf(MigrationLock.lockedScripts(MigrationLock.LOCK));
  }

  @Test
  void migrationNamesFollowTheConvention() {
    for (MigrationInfo info : flyway.info().applied()) {
      if (info.getVersion() != null) {
        assertThat(info.getScript()).matches("V\\d{12}__[a-z]+_[a-z0-9_]+\\.sql");
      }
    }
  }
}
