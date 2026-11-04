// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.testsupport.AbstractIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Onde se escribe o rexistro de actividade respecto da transacción do caso de uso (ADR-0020): o
 * éxito vai con ela (se se desfai, non queda) e o fallo queda sempre.
 */
class AuditLogTransactionIT extends AbstractIT {

  @Autowired AuditLog audit;
  @Autowired TransactionTemplate tx;

  @Test
  void aSuccessIsOnlyKeptIfItsTransactionCommits() {
    String action = "test.audit-" + UUID.randomUUID();
    tx.executeWithoutResult(
        status -> {
          audit.record(new AuditEvent(action, Outcome.SUCCESS, null, null, Map.of()));
          status.setRollbackOnly();
        });
    assertThat(count(action, "SUCCESS")).isZero();

    tx.executeWithoutResult(
        status -> audit.record(new AuditEvent(action, Outcome.SUCCESS, null, null, Map.of())));
    assertThat(count(action, "SUCCESS")).isEqualTo(1);
  }

  @Test
  void aFailureIsKeptWhenItsTransactionRollsBack() {
    String action = "test.audit-" + UUID.randomUUID();
    tx.executeWithoutResult(
        status -> {
          audit.record(new AuditEvent(action, Outcome.FAILURE, null, null, Map.of()));
          status.setRollbackOnly();
        });
    assertThat(count(action, "FAILURE")).isEqualTo(1);
  }

  @Test
  void outsideATransactionOrReadOnlyItIsKept() {
    String action = "test.audit-" + UUID.randomUUID();
    audit.record(new AuditEvent(action, Outcome.SUCCESS, null, null, Map.of()));
    TransactionTemplate readOnly = new TransactionTemplate(tx.getTransactionManager());
    readOnly.setReadOnly(true);
    readOnly.executeWithoutResult(
        status -> audit.record(new AuditEvent(action, Outcome.SUCCESS, null, null, Map.of())));
    assertThat(count(action, "SUCCESS")).isEqualTo(2);
  }

  private int count(String action, String outcome) {
    return jdbc.sql("select count(*) from audit_event where action = :a and outcome = :o")
        .param("a", action)
        .param("o", outcome)
        .query(Integer.class)
        .single();
  }
}
