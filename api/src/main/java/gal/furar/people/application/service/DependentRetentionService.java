// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.DependentUsage;
import gal.furar.people.NoDependentUsage;
import gal.furar.people.application.port.out.AccountEmails;
import gal.furar.people.application.port.out.DependentRepository;
import gal.furar.shared.tenancy.PlatformTask;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conservación das persoas a cargo e das contas coñecidas polo correo (ADR-0031, puntos 4 e 6;
 * docs/datos-persoais.md; RGPD art. 5.1.e). Unha vez ao día:
 *
 * <ul>
 *   <li>borra as fichas desactivadas que xa non figuran en ningunha reserva: as súas reservas
 *       anonimizáronse e soltaron a ficha ({@code BookingRetentionService}); cada unha na súa
 *       transacción;
 *   <li>borra as contas coñecidas que non entran desde hai máis de {@code furar.booking.retention}
 *       (P2Y, o prazo das reservas): unha conta viva volve escribirse en canto entra.
 * </ul>
 *
 * Idempotente. O rexistro técnico só leva cantas: nin contas nin nomes.
 */
@Service
@PlatformTask
public class DependentRetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(DependentRetentionService.class);

  private final DependentRepository dependents;
  private final AccountEmails accounts;
  private final DependentUsage usage;
  private final TransactionTemplate tx;
  private final Period retention;
  private final Clock clock;

  public DependentRetentionService(
      DependentRepository dependents,
      AccountEmails accounts,
      ObjectProvider<DependentUsage> usage,
      PlatformTransactionManager transactions,
      @Value("${furar.booking.retention:P2Y}") Period retention,
      Clock clock) {
    this.dependents = dependents;
    this.accounts = accounts;
    this.usage = usage.getIfAvailable(NoDependentUsage::new);
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retention = retention;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.people.retention-cron:0 45 4 * * *}")
  void scheduled() {
    apply();
  }

  /** Aplica a conservación e devolve cantas fichas borrou. */
  public int apply() {
    List<UUID> candidates =
        Objects.requireNonNull(tx.execute(status -> dependents.deactivated()), "deactivated");
    int purged = 0;
    for (UUID id : candidates) {
      purged += Objects.requireNonNull(tx.execute(status -> purge(id)), "purge");
    }
    Instant cutoff = clock.instant().atOffset(ZoneOffset.UTC).minus(retention).toInstant();
    int accountsPurged =
        Objects.requireNonNull(tx.execute(status -> accounts.purgeSeenBefore(cutoff)), "accounts");
    LOG.info(
        "people retention retention={} cutoff={} dependents={} accounts={}",
        retention,
        cutoff,
        purged,
        accountsPurged);
    return purged;
  }

  private int purge(UUID id) {
    if (usage.isUsed(id)) {
      return 0;
    }
    dependents.purge(id);
    return 1;
  }
}
