// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.NoShowLedger;
import gal.furar.facility.FacilityQueries;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A falta automática (ADR-0032, punto 5): nas instalacións con control de entrada, a reserva que
 * remata sen validar pasa soa a «non presentado». Cada 10 minutos, en cada concello e coa súa RLS,
 * marca as confirmadas sen marca e nunca tocadas que empezaron despois de acender o control de
 * entrada e cuxa fin máis a marxe de validación xa pasou.
 *
 * <p>En lotes de {@value #BATCH} reservas, cada un na súa transacción curta. O lote colle as filas
 * con {@code for update skip locked} e a actualización repite as condicións: unha validación ou
 * unha cancelación que chega á vez non se pisa (quen ten a fila gaña e a tarefa sáltaa). Cada falta
 * con correo conta para a penalización do concello e avísase por correo despois do commit; sen
 * correo, nin unha cousa nin a outra.
 *
 * <p>Cada lote deixa un evento {@value #NO_SHOW} no rexistro de actividade coa tarefa como quen
 * actúa ({@value #SUBJECT}) e só cantas e cantos bloqueos. Idempotente: unha falta desfeita non se
 * volve marcar. Un fallo nun concello non para os demais.
 */
@Service
@PlatformTask
public class NoShowService {

  /** Quen actúa na marca, na historia e no rexistro de actividade: a propia tarefa. */
  public static final String SUBJECT = AuditEvent.SYSTEM_PREFIX + "no-show";

  /** Acción do rexistro de actividade dun lote de faltas. */
  public static final String NO_SHOW = AttendanceService.NO_SHOW;

  /** Reservas por transacción. */
  static final int BATCH = 500;

  private static final Logger LOG = LoggerFactory.getLogger(NoShowService.class);

  private final MunicipalityDirectory municipalities;
  private final FacilityQueries facilities;
  private final NoShowLedger ledger;
  private final NoShowPenalty penalty;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;
  private final Duration margin;
  private final Clock clock;

  public NoShowService(
      MunicipalityDirectory municipalities,
      FacilityQueries facilities,
      NoShowLedger ledger,
      NoShowPenalty penalty,
      AuditLog audit,
      ApplicationEventPublisher events,
      PlatformTransactionManager transactions,
      @Value("${furar.booking.validation-margin:PT45M}") Duration margin,
      Clock clock) {
    this.municipalities = municipalities;
    this.facilities = facilities;
    this.ledger = ledger;
    this.penalty = penalty;
    this.audit = audit;
    this.events = events;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.margin = margin;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.booking.no-show-cron:0 */10 * * * *}")
  void scheduled() {
    apply();
  }

  /** Marca as faltas en todos os concellos e devolve cantas marcou. */
  public int apply() {
    Instant now = clock.instant();
    // A fin máis a marxe xa pasou.
    Instant endedBy = now.minus(margin);
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        int done =
            TenantContext.runAs(municipality, () -> municipality(municipality, endedBy, now));
        if (done > 0) {
          LOG.info("no-show municipality={} marked={}", municipality.id(), done);
        }
        total += done;
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "no-show failed municipality={} error={}", municipality.id(), e.getClass().getName());
      }
    }
    if (total > 0 || failed > 0) {
      LOG.info("no-show municipalities={} failed={} marked={}", all.size(), failed, total);
    }
    return total;
  }

  private int municipality(TenantRef municipality, Instant endedBy, Instant now) {
    int done = 0;
    for (FacilityQueries.EntryControl facility : facilities.entryControl()) {
      int last;
      do {
        last =
            Objects.requireNonNull(
                tx.execute(s -> batch(municipality, facility, endedBy, now)), "batch");
        done += last;
      } while (last == BATCH);
    }
    return done;
  }

  private int batch(
      TenantRef municipality, FacilityQueries.EntryControl facility, Instant endedBy, Instant now) {
    List<NoShowLedger.Marked> marked =
        ledger.markEnded(facility.facilityId(), facility.since(), endedBy, BATCH, now, SUBJECT);
    if (marked.isEmpty()) {
      return 0;
    }
    int blocks = 0;
    for (NoShowLedger.Marked fault : marked) {
      String emailKey = fault.emailKey();
      if (emailKey == null) {
        continue;
      }
      NoShowPenalty.Fault counted = penalty.afterFault(emailKey, now);
      if (counted.blockedNow()) {
        blocks++;
      }
      events.publishEvent(penalty.event(fault.id(), fault.eventId(), counted.standing()));
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("count", marked.size());
    details.put("blocks", blocks);
    details.put("facilityId", facility.facilityId().toString());
    audit.record(
        AuditEvent.bySystem(
            SUBJECT, NO_SHOW, Outcome.SUCCESS, municipality.id(), municipality.slug(), details));
    return marked.size();
  }
}
