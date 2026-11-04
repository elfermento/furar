// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaDialect;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * {@code JpaTransactionManager} do concello (ADR-0006, ARQ2-1). Usa o dialecto que fixa o concello
 * da Row Level Security ao comezar cada transacción ({@code RowLevelSecurityJpaDialect}) e lembra
 * con que concello comezou cada unha, para que {@link TenantContext} impida cambiar de concello
 * dentro dela (caso d de {@code TenantRlsSpringIT}).
 */
public class TenantJpaTransactionManager extends JpaTransactionManager {

  private static final long serialVersionUID = 1L;

  private final transient JpaDialect rowLevelSecurityDialect;

  public TenantJpaTransactionManager(EntityManagerFactory emf, JpaDialect rowLevelSecurityDialect) {
    super(emf);
    this.rowLevelSecurityDialect = rowLevelSecurityDialect;
    setJpaDialect(rowLevelSecurityDialect);
  }

  @Override
  public void afterPropertiesSet() {
    super.afterPropertiesSet();
    // afterPropertiesSet copia o dialecto da EntityManagerFactory: vólvese poñer o da RLS.
    setJpaDialect(rowLevelSecurityDialect);
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    super.doBegin(transaction, definition);
    TransactionTenant.begin(TenantContext.snapshot());
  }

  @Override
  protected void doCommit(DefaultTransactionStatus status) {
    try {
      super.doCommit(status);
    } finally {
      TransactionTenant.complete();
    }
  }

  @Override
  protected void doRollback(DefaultTransactionStatus status) {
    try {
      super.doRollback(status);
    } finally {
      TransactionTenant.complete();
    }
  }

  @Override
  protected void doCleanupAfterCompletion(Object transaction) {
    try {
      super.doCleanupAfterCompletion(transaction);
    } finally {
      TransactionTenant.end();
    }
  }
}
