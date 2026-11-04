// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.adapter.out.persistence.JdbcAuditRetention;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.config.AuditProperties;
import gal.furar.testsupport.AbstractIT;
import java.time.Period;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Conservación do rexistro de actividade (ADR-0020, RGPD art. 5.1.e, SEC4-03, AP4-02): a tarefa
 * borra o que pasou de {@code furar.audit.retention} (P3Y por defecto, revisión do 2026-10-29) e
 * nada máis, tampouco o bloqueado (entre un e tres anos); a función da base nunca borra nada con
 * menos dun ano, nin pedíndoo como {@code furar_app}; e a API segue sen DELETE en {@code
 * audit_event}.
 */
class AuditRetentionIT extends AbstractIT {

  @Autowired JdbcAuditRetention retention;
  @Autowired AuditProperties properties;

  @Test
  void purgesOnlyWhatIsOlderThanTheRetention() {
    assertThat(properties.visible()).isEqualTo(Period.ofYears(1));
    assertThat(properties.retention()).isEqualTo(Period.ofYears(3));
    UUID old = insertEvent("1100 days");
    UUID blocked = insertEvent("1000 days");
    UUID recent = insertEvent("300 days");

    assertThat(retention.purge()).isPositive();

    assertThat(exists(old)).isFalse();
    assertThat(exists(blocked)).as("blocked, not yet deleted").isTrue();
    assertThat(exists(recent)).isTrue();
  }

  @Test
  void databaseNeverPurgesTheLastYearEvenIfAsked() {
    UUID young = insertEvent("300 days");
    UUID older = insertEvent("400 days");

    appJdbc.sql("select purge_audit_events(now() + interval '1 day')").query(Long.class).single();

    assertThat(exists(young)).as("under a year: kept whatever furar_app asks").isTrue();
    assertThat(exists(older)).isFalse();
    assertThatThrownBy(() -> appJdbc.sql("delete from audit_event").update())
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void retentionBelowTheMinimumOrNotLongerThanVisibleDoesNotStart() {
    assertThatThrownBy(
            () -> new AuditProperties(Period.ofMonths(1), Period.ofMonths(11), "0 30 3 * * *"))
        .hasMessageContaining("at least " + AuditRetention.MINIMUM_RETENTION);
    assertThatThrownBy(
            () -> new AuditProperties(Period.ofYears(2), Period.ofYears(2), "0 30 3 * * *"))
        .hasMessageContaining("must be longer than furar.audit.visible");
  }

  private UUID insertEvent(String age) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into audit_event (id, occurred_at, actor_subject, action, outcome)
            values (:id, now() - cast(:age as interval), 'test-retention', 'test.retention',
                    'SUCCESS')
            """)
        .param("id", id)
        .param("age", age)
        .update();
    return id;
  }

  private boolean exists(UUID id) {
    return jdbc.sql("select count(*) from audit_event where id = :id")
            .param("id", id)
            .query(Integer.class)
            .single()
        == 1;
  }
}
