// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRetention;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
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
 * Conservación das reservas (ADR-0017 punto 5, revisión do 2026-11-04; docs/datos-persoais.md; RGPD
 * art. 5.1.e). Unha vez ao día, en cada concello e coa súa RLS, anonimiza as reservas cuxa franxa
 * rematou e que non cambiaron (un cobro, unha devolución) hai máis de {@code
 * furar.booking.retention} (P2Y por defecto) ou, as de pago, de {@code
 * furar.booking.paid-retention} (P5Y: a normativa tributaria).
 *
 * <p>Anonimizar é, na mesma transacción: borrar os valores dos campos da reserva marcados como dato
 * persoal; quitar a persoa (conta, nome e correo a nulo, {@code anonymized_at}); e, onde actuou ela
 * (quen cancelou, as filas da historia), {@value BookingRetention#ANONYMISED_ACTOR}. Quedan a
 * instalación, o uso, a franxa, o estado, o prezo e o cobro: os indicadores seguen contando a
 * reserva, pero xa non a persoa («persoas distintas» non conta as anonimizadas).
 *
 * <p>En lotes de {@value #BATCH} reservas, cada un na súa transacción curta, ata que non queda
 * ningunha; cada lote deixa un evento {@value #ANONYMIZE} no rexistro de actividade coa tarefa como
 * quen actúa ({@value #SUBJECT}) e só cantas, se eran de pago e o corte: nin códigos nin persoas.
 *
 * <p>Idempotente: unha segunda pasada non atopa nada novo. O rexistro técnico só leva o concello e
 * cantas. Un fallo nun concello non para os demais: queda no rexistro e repítese ao día seguinte.
 */
@Service
@PlatformTask
public class BookingRetentionService {

  /** Quen actúa no rexistro de actividade: a propia tarefa, non unha persoa. */
  public static final String SUBJECT = AuditEvent.SYSTEM_PREFIX + "booking-retention";

  /** Acción do rexistro de actividade ao anonimizar un lote de reservas. */
  public static final String ANONYMIZE = "booking.anonymize";

  /** Reservas por transacción. */
  static final int BATCH = 500;

  private static final Logger LOG = LoggerFactory.getLogger(BookingRetentionService.class);

  private final MunicipalityDirectory municipalities;
  private final BookingRetention bookings;
  private final BookingCustomFields customFields;
  private final AuditLog audit;
  private final TransactionTemplate tx;
  private final Period retention;
  private final Period paidRetention;
  private final Clock clock;

  public BookingRetentionService(
      MunicipalityDirectory municipalities,
      BookingRetention bookings,
      BookingCustomFields customFields,
      AuditLog audit,
      PlatformTransactionManager transactions,
      @Value("${furar.booking.retention:P2Y}") Period retention,
      @Value("${furar.booking.paid-retention:P5Y}") Period paidRetention,
      Clock clock) {
    this.municipalities = municipalities;
    this.bookings = bookings;
    this.customFields = customFields;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retention = retention;
    this.paidRetention = paidRetention;
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.booking.retention-cron:0 30 4 * * *}")
  void scheduled() {
    apply();
  }

  /** Aplica a conservación en todos os concellos e devolve cantas reservas anonimizou. */
  public int apply() {
    Instant now = clock.instant();
    Instant cutoff = now.atOffset(ZoneOffset.UTC).minus(retention).toInstant();
    Instant paidCutoff = now.atOffset(ZoneOffset.UTC).minus(paidRetention).toInstant();
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        int done =
            TenantContext.runAs(
                municipality,
                () ->
                    all(municipality, false, cutoff, now)
                        + all(municipality, true, paidCutoff, now));
        if (done > 0) {
          LOG.info("booking retention municipality={} anonymised={}", municipality.id(), done);
        }
        total += done;
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "booking retention failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    LOG.info(
        "booking retention retention={} paidRetention={} cutoff={} paidCutoff={} municipalities={}"
            + " failed={} anonymised={}",
        retention,
        paidRetention,
        cutoff,
        paidCutoff,
        all.size(),
        failed,
        total);
    return total;
  }

  /** Lote tras lote, ata que un vén incompleto. */
  private int all(TenantRef municipality, boolean paid, Instant cutoff, Instant now) {
    int done = 0;
    int last;
    do {
      last =
          Objects.requireNonNull(tx.execute(s -> batch(municipality, paid, cutoff, now)), "batch");
      done += last;
    } while (last == BATCH);
    return done;
  }

  private int batch(TenantRef municipality, boolean paid, Instant cutoff, Instant now) {
    List<BookingId> expired = bookings.expired(paid, cutoff, BATCH);
    if (expired.isEmpty()) {
      return 0;
    }
    // Os campos primeiro: a reserva aínda é do concello e ten a súa persoa.
    expired.forEach(customFields::erasePersonalData);
    int anonymised = bookings.anonymise(expired, now);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("count", anonymised);
    details.put("paid", paid);
    details.put("cutoff", cutoff.toString());
    details.put("reason", "retention");
    audit.record(
        AuditEvent.bySystem(
            SUBJECT, ANONYMIZE, Outcome.SUCCESS, municipality.id(), municipality.slug(), details));
    // O tamaño do lote, aínda que algunha xa estivese anonimizada: decide se hai outro.
    return expired.size();
  }
}
