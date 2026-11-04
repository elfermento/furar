// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.IdempotencyKeys;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caducidade das claves de reintento de «reservar» (ADR-0025, punto 19). Cada hora, en cada
 * concello e coa súa RLS (unha transacción curta por concello), borra as filas de {@code
 * booking_idempotency_key} gardadas hai máis de {@code furar.booking.idempotency-retention} (PT24H
 * por defecto). Así unha clave vale polo menos 24 horas e como moito unha hora máis; despois, a
 * mesma clave sería unha intención nova. Levan a conta de quen reservou ({@code holder_subject}) e
 * non serven para outra cousa: bórranse, non se anonimizan (RGPD art. 5.1.e).
 *
 * <p>Idempotente. O rexistro técnico só leva o concello e cantas filas; un fallo nun concello non
 * para os demais e repítese na hora seguinte.
 */
@Service
@PlatformTask
public class IdempotencyKeyRetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyRetentionService.class);

  private final MunicipalityDirectory municipalities;
  private final IdempotencyKeys keys;
  private final TransactionTemplate tx;
  private final Duration retention;
  private final Clock clock;

  public IdempotencyKeyRetentionService(
      MunicipalityDirectory municipalities,
      IdempotencyKeys keys,
      PlatformTransactionManager transactions,
      @Value("${furar.booking.idempotency-retention:PT24H}") Duration retention,
      Clock clock) {
    this.municipalities = municipalities;
    this.keys = keys;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retention = retention;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.booking.idempotency-retention-cron:0 20 * * * *}")
  void scheduled() {
    apply();
  }

  /** Borra as claves caducadas en todos os concellos e devolve cantas. */
  public int apply() {
    Instant cutoff = clock.instant().minus(retention);
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        int deleted =
            TenantContext.runAs(
                municipality,
                () -> Objects.requireNonNull(tx.execute(s -> keys.purgeCreatedBefore(cutoff))));
        if (deleted > 0) {
          LOG.info(
              "idempotency key retention municipality={} deleted={}", municipality.id(), deleted);
        }
        total += deleted;
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "idempotency key retention failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    LOG.debug(
        "idempotency key retention retention={} cutoff={} municipalities={} failed={} deleted={}",
        retention,
        cutoff,
        all.size(),
        failed,
        total);
    return total;
  }
}
