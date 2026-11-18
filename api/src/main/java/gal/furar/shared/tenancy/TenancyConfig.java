// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.orm.jpa.JpaDialect;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;

/** Concello nas transaccións (RLS) e nos escoitadores de eventos. */
@Configuration(proxyBeanMethods = false)
class TenancyConfig {

  /**
   * Substitúe o {@code JpaTransactionManager} de Spring Boot: cada transacción leva o concello á
   * RLS (ADR-0006).
   */
  @Bean
  PlatformTransactionManager transactionManager(
      EntityManagerFactory entityManagerFactory, JpaDialect rowLevelSecurityDialect) {
    return new TenantJpaTransactionManager(entityManagerFactory, rowLevelSecurityDialect);
  }

  /**
   * Envolve todo {@code @TransactionalEventListener} (tamén {@code @ApplicationModuleListener}) co
   * concello do evento. Orde máxima: vai por dentro do {@code @Async} e por fóra de
   * {@code @Transactional}.
   */
  @Bean
  static Advisor tenantListenerAdvisor() {
    DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(
            new AnnotationMatchingPointcut(null, TransactionalEventListener.class, true),
            new TenantListenerInterceptor());
    advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return advisor;
  }
}
