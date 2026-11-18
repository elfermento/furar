// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Conservación das inscricións nos eventos (ADR-0038; docs/datos-persoais.md; RGPD art. 5.1.e).
 * Unha vez ao día, en cada concello e coa súa RLS: quita a persoa (conta, nome, correo, persoa a
 * cargo e ano de nacemento) das inscricións dos eventos que remataron hai máis de {@code
 * furar.event.retention} (P2Y, o prazo das reservas): un evento dun día, desde que empezou; unha
 * actividade con horario, desde a última sesión (ADR-0041). Quedan o evento, o estado, o pago e a
 * asistencia, sen persoa. Ademais borra as esperas que non chegaron a praza dos eventos que xa non
 * admiten inscricións ou que se cancelaron hai máis dun día (o aviso da cancelación xa saíu).
 *
 * <p>Cada concello, nunha transacción; se toca algo, deixa un evento {@value #ANONYMIZE} no
 * rexistro de actividade coa tarefa como quen actúa e só cantas e o corte. Idempotente. Un fallo
 * nun concello non para os demais.
 */
@Service
@PlatformTask
public class EventRetentionService {

  /** Quen actúa no rexistro de actividade: a propia tarefa. */
  public static final String SUBJECT = AuditEvent.SYSTEM_PREFIX + "event-retention";

  /** Acción do rexistro de actividade ao anonimizar inscricións vellas. */
  public static final String ANONYMIZE = "event.anonymize";

  /** Acción do rexistro de actividade ao borrar as esperas dos eventos pechados (ADR-0041). */
  public static final String WAITS_REMOVE = "event.waits-remove";

  private static final Logger LOG = LoggerFactory.getLogger(EventRetentionService.class);

  private final MunicipalityDirectory municipalities;
  private final RegistrationRepository registrations;
  private final AuditLog audit;
  private final TransactionTemplate tx;
  private final Period retention;
  private final Clock clock;

  public EventRetentionService(
      MunicipalityDirectory municipalities,
      RegistrationRepository registrations,
      AuditLog audit,
      PlatformTransactionManager transactions,
      @Value("${furar.event.retention:P2Y}") Period retention,
      Clock clock) {
    this.municipalities = municipalities;
    this.registrations = registrations;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retention = retention;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.event.retention-cron:0 55 4 * * *}")
  void scheduled() {
    apply();
  }

  /** Aplica a conservación en todos os concellos e devolve cantas inscricións tocou. */
  public int apply() {
    Instant now = clock.instant();
    Instant cutoff = now.atOffset(ZoneOffset.UTC).minus(retention).toInstant();
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    for (TenantRef municipality : all) {
      try {
        total +=
            TenantContext.runAs(
                municipality,
                () ->
                    Objects.requireNonNull(
                        tx.execute(s -> anonymize(municipality, cutoff, now)), "anonymize"));
      } catch (RuntimeException e) {
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "event retention failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    return total;
  }

  private int anonymize(TenantRef municipality, Instant cutoff, Instant now) {
    int waits = registrations.deleteClosedWaits(now, now.minus(Duration.ofDays(1)));
    if (waits > 0) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("removed", waits);
      details.put("reason", "closed");
      audit.record(
          AuditEvent.bySystem(
              SUBJECT,
              WAITS_REMOVE,
              Outcome.SUCCESS,
              municipality.id(),
              municipality.slug(),
              details));
    }
    int count = registrations.anonymizeEndedBefore(cutoff, now);
    if (count == 0) {
      return waits;
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("anonymized", count);
    details.put("cutoff", cutoff.toString());
    details.put("reason", "retention");
    audit.record(
        AuditEvent.bySystem(
            SUBJECT, ANONYMIZE, Outcome.SUCCESS, municipality.id(), municipality.slug(), details));
    LOG.info("event retention municipality={} anonymized={}", municipality.id(), count);
    return count + waits;
  }
}
