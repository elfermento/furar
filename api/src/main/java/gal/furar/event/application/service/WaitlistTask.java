// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.application.port.in.SendEventMails;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.Registration;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A tarefa da lista de espera dos eventos (ADR-0041), cada minuto en cada concello coa súa RLS: pon
 * ao día a lista de cada evento aberto con xente esperando ({@link Waitlist#fill}: pasada a hora de
 * exclusividade, avisa á seguinte quenda; cubertas as prazas, as avisadas volven a esperar; unha
 * praza que quedou libre sen pasar por un caso de uso, como a baixa dunha conta, avísase) e manda
 * os correos dos avisos que xa corren e aínda non saíron (os da noite, ás 8:00). Cada evento, baixo
 * o bloqueo da súa fila e nunha transacción curta; os correos, despois e fóra de toda transacción.
 * Idempotente: un fallo nun concello non para os demais.
 */
@Service
@PlatformTask
public class WaitlistTask {

  private static final Logger LOG = LoggerFactory.getLogger(WaitlistTask.class);

  private final MunicipalityDirectory municipalities;
  private final EventRepository events;
  private final RegistrationRepository registrations;
  private final Waitlist waitlist;
  private final SendEventMails mails;
  private final TransactionTemplate tx;
  private final Clock clock;

  public WaitlistTask(
      MunicipalityDirectory municipalities,
      EventRepository events,
      RegistrationRepository registrations,
      Waitlist waitlist,
      SendEventMails mails,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.municipalities = municipalities;
    this.events = events;
    this.registrations = registrations;
    this.waitlist = waitlist;
    this.mails = mails;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.event.waitlist-cron:30 * * * * *}")
  void scheduled() {
    apply();
  }

  /** Pasa a tarefa en todos os concellos e devolve cantos avisos novos deu. */
  public int apply() {
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    for (TenantRef municipality : all) {
      try {
        total += TenantContext.runAs(municipality, this::inMunicipality);
      } catch (RuntimeException e) {
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "event waitlist failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    return total;
  }

  private int inMunicipality() {
    Instant now = clock.instant();
    List<EventId> open =
        Objects.requireNonNull(
            tx.execute(s -> registrations.eventsWithWaiting(now)), "eventsWithWaiting");
    int notified = 0;
    for (EventId id : open) {
      notified +=
          Objects.requireNonNull(
              tx.execute(
                  s -> {
                    if (!registrations.lockEvent(id)) {
                      return 0;
                    }
                    return events.findById(id).map(e -> waitlist.fill(e, now).size()).orElse(0);
                  }),
              "fill");
    }
    List<UUID> toMail =
        Objects.requireNonNull(
            tx.execute(
                s ->
                    registrations.noticesToMail(now).stream()
                        .map(Registration::id)
                        .map(r -> r.value())
                        .toList()),
            "noticesToMail");
    toMail.forEach(mails::waitlistNotified);
    return notified;
  }
}
