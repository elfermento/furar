// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.BookingWaits;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
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
 * Ao pasar a hora, a espera bórrase e queda o reconto (ADR-0033, punto 8). Cada hora, en cada
 * concello e coa súa RLS (unha transacción curta por concello), bórranse as esperas cuxa hora xa
 * empezou ({@code delete … returning}) e súmase cantas eran ao reconto anónimo (uso, franxa e
 * número): a «demanda non atendida». Quen deixou de esperar, reservou esa hora ou perdeu a hora por
 * un bloqueo non conta. Mentres a tarefa non pasa, as lecturas xa non mostran nin avisan as
 * vencidas.
 *
 * <p>A mesma pasada borra as filas do reconto de franxas de hai máis de {@code
 * furar.booking.wait-tally-retention} (P5Y por defecto) e as esperas canceladas pendentes de avisar
 * de hai máis de {@code furar.booking.cancelled-wait-retention} (P7D por defecto): o correo xa saíu
 * ou xa non sairá.
 *
 * <p>Idempotente. O rexistro técnico só leva o concello e cantas filas; un fallo nun concello non
 * para os demais e repítese na hora seguinte.
 */
@Service
@PlatformTask
public class WaitExpiryService {

  private static final Logger LOG = LoggerFactory.getLogger(WaitExpiryService.class);

  private final MunicipalityDirectory municipalities;
  private final BookingWaits waits;
  private final TransactionTemplate tx;
  private final Period tallyRetention;
  private final Period cancelledRetention;
  private final Clock clock;

  public WaitExpiryService(
      MunicipalityDirectory municipalities,
      BookingWaits waits,
      PlatformTransactionManager transactions,
      @Value("${furar.booking.wait-tally-retention:P5Y}") Period tallyRetention,
      @Value("${furar.booking.cancelled-wait-retention:P7D}") Period cancelledRetention,
      Clock clock) {
    this.municipalities = municipalities;
    this.waits = waits;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.tallyRetention = tallyRetention;
    this.cancelledRetention = cancelledRetention;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.booking.wait-expiry-cron:0 5 * * * *}")
  void scheduled() {
    apply();
  }

  /** Vence as esperas pasadas en todos os concellos e devolve cantas. */
  public int apply() {
    Instant now = clock.instant();
    Instant cutoff = now.atOffset(ZoneOffset.UTC).minus(tallyRetention).toInstant();
    Instant cancelledCutoff = now.atOffset(ZoneOffset.UTC).minus(cancelledRetention).toInstant();
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        int expired =
            TenantContext.runAs(
                municipality,
                () ->
                    Objects.requireNonNull(
                        tx.execute(
                            s -> {
                              int gone = waits.expire(now);
                              waits.purgeTallyBefore(cutoff);
                              waits.purgeCancelledBefore(cancelledCutoff);
                              return gone;
                            })));
        if (expired > 0) {
          LOG.info("wait expiry municipality={} expired={}", municipality.id(), expired);
        }
        total += expired;
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "wait expiry failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    LOG.debug(
        "wait expiry now={} municipalities={} failed={} expired={}",
        now,
        all.size(),
        failed,
        total);
    return total;
  }
}
