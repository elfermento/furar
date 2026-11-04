// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.notification.application.port.out.DeliveryLog;
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
 * Conservación do rexistro de correos (docs/datos-persoais.md, «Avisos do servizo»; RGPD art.
 * 5.1.e). Unha vez ao día, en cada concello e coa súa RLS (unha transacción curta por concello),
 * borra as filas de {@code notification_delivery} rexistradas hai máis de {@code
 * furar.notification.retention} (P1Y por defecto): levan o correo de destino.
 *
 * <p><b>Borrar e non anonimizar:</b> a fila só serve para non enviar dúas veces o mesmo correo (un
 * reintento do rexistro de publicacións, que chega en minutos, ou unha invitación, que caduca aos 7
 * días). Despois dun ano ninguén a le: nin a xestión, nin a API, nin {@code furar-export}; unha
 * fila anonimizada non lle serviría a ninguén. As invitacións borradas ou as pertenzas anonimizadas
 * antes do ano esquécense xa na conservación de {@code staff} ({@code
 * StaffInvitationMailer.forget}).
 *
 * <p><b>Non segue os tres anos do rexistro de actividade</b> (ADR-0020, revisión do 2026-10-29):
 * esta táboa non é proba de ningunha actuación do persoal (iso é {@code audit_event}, que garda
 * quen invitou e cando); só evita reenvíos. Gardala máis tempo sería gardar correos sen finalidade
 * (RGPD art. 5.1.c e 5.1.e), así que queda nun ano.
 *
 * <p>Idempotente. O rexistro técnico só leva o concello e cantas filas; un fallo nun concello non
 * para os demais e repítese ao día seguinte.
 */
@Service
@PlatformTask
public class NotificationRetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationRetentionService.class);

  private final MunicipalityDirectory municipalities;
  private final DeliveryLog log;
  private final TransactionTemplate tx;
  private final Period retention;
  private final Clock clock;

  public NotificationRetentionService(
      MunicipalityDirectory municipalities,
      DeliveryLog log,
      PlatformTransactionManager transactions,
      @Value("${furar.notification.retention:P1Y}") Period retention,
      Clock clock) {
    this.municipalities = municipalities;
    this.log = log;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retention = retention;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.notification.retention-cron:0 15 4 * * *}")
  void scheduled() {
    apply();
  }

  /** Aplica a conservación en todos os concellos e devolve cantos envíos borrou. */
  public int apply() {
    Instant cutoff = clock.instant().atOffset(ZoneOffset.UTC).minus(retention).toInstant();
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        int deleted =
            TenantContext.runAs(
                municipality,
                () -> Objects.requireNonNull(tx.execute(s -> log.purgeCreatedBefore(cutoff))));
        if (deleted > 0) {
          LOG.info("notification retention municipality={} deleted={}", municipality.id(), deleted);
        }
        total += deleted;
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "notification retention failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    LOG.info(
        "notification retention retention={} cutoff={} municipalities={} failed={} deleted={}",
        retention,
        cutoff,
        all.size(),
        failed,
        total);
    return total;
  }
}
