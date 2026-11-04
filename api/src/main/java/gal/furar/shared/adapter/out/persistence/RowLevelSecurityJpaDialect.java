// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import org.jspecify.annotations.Nullable;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;

/**
 * Dialecto de Hibernate que fixa o concello da Row Level Security xusto despois de comezar cada
 * transacción (ADR-0006, ARQ2-1): {@code set_config('furar.municipality_id', id, true)} na conexión
 * da transacción, a mesma que usan JPA e {@code JdbcClient}. Dura ata o commit ou o rollback. Sen
 * concello ou como plataforma non fixa nada: a variable queda {@code null} e as táboas con RLS non
 * devolven nin aceptan filas. Se falla, {@code JpaTransactionManager} pecha o {@code EntityManager}
 * como con calquera outro erro ao comezar. Úsao {@code TenantJpaTransactionManager}.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason = "Fixa a variable de sesión da RLS ao comezar a transacción; non le nin escribe táboas")
public class RowLevelSecurityJpaDialect extends HibernateJpaDialect {

  private static final long serialVersionUID = 1L;

  @Override
  public @Nullable Object beginTransaction(EntityManager em, TransactionDefinition definition)
      throws SQLException {
    Object data = super.beginTransaction(em, definition);
    TenantContext.current()
        .ifPresent(
            tenant ->
                em.createNativeQuery("select set_config('furar.municipality_id', :id, true)")
                    .setParameter("id", tenant.id().toString())
                    .getSingleResult());
    return data;
  }
}
