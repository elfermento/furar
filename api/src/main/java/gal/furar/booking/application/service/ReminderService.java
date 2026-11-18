// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingReminderDue;
import gal.furar.booking.application.port.out.BookingReminders;
import gal.furar.booking.domain.Reminder;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O recordatorio por correo da reserva (ADR-0035). Cada 10 minutos, en cada concello e coa súa RLS,
 * marca as reservas ás que lles toca ({@link Reminder}: 24 horas antes do prazo para cancelar de
 * balde ou, sen prazo, do inicio) e publica un {@link BookingReminderDue} por cada unha que non
 * sexa dun correo que deu de baixa os recordatorios. O correo sae despois do commit.
 *
 * <p>En lotes de {@value #BATCH}, cada un na súa transacción curta. O lote colle as filas con
 * {@code for update skip locked} e a marca repite as condicións: dúas instancias á vez non mandan o
 * mesmo recordatorio, e unha cancelación que chega á vez gaña. A marca queda aínda que o correo se
 * dese de baixa: nunca se manda dúas veces. Unha reserva feita despois do momento do recordatorio,
 * ou xa cancelada, non o recibe. Idempotente; un fallo nun concello non para os demais. O rexistro
 * técnico só leva o concello e cantas.
 */
@Service
@PlatformTask
public class ReminderService {

  /** Reservas por transacción. */
  static final int BATCH = 500;

  private static final Logger LOG = LoggerFactory.getLogger(ReminderService.class);

  private final MunicipalityDirectory municipalities;
  private final BookingReminders reminders;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;
  private final Clock clock;

  public ReminderService(
      MunicipalityDirectory municipalities,
      BookingReminders reminders,
      ApplicationEventPublisher events,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.municipalities = municipalities;
    this.reminders = reminders;
    this.events = events;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.booking.reminder-cron:0 */10 * * * *}")
  void scheduled() {
    apply();
  }

  /** Dá os recordatorios que tocan en todos os concellos e devolve cantos correos pediu. */
  public int apply() {
    Instant now = clock.instant();
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        int done = TenantContext.runAs(municipality, () -> municipality(municipality, now));
        if (done > 0) {
          LOG.info("reminders municipality={} sent={}", municipality.id(), done);
        }
        total += done;
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "reminders failed municipality={} error={}", municipality.id(), e.getClass().getName());
      }
    }
    if (failed > 0) {
      LOG.info("reminders municipalities={} failed={} sent={}", all.size(), failed, total);
    }
    return total;
  }

  private int municipality(TenantRef municipality, Instant now) {
    int sent = 0;
    int[] marked = new int[1];
    do {
      sent +=
          Objects.requireNonNull(
              tx.execute(
                  s -> {
                    List<BookingReminders.Marked> due =
                        reminders.markDue(now, Reminder.LEAD, BATCH);
                    marked[0] = due.size();
                    Set<String> optedOut =
                        reminders.optedOutAmong(
                            due.stream()
                                .map(BookingReminders.Marked::emailKey)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()));
                    int published = 0;
                    for (BookingReminders.Marked m : due) {
                      if (m.emailKey() != null && !optedOut.contains(m.emailKey())) {
                        events.publishEvent(
                            new BookingReminderDue(municipality.id(), m.bookingId()));
                        published++;
                      }
                    }
                    return published;
                  }),
              "batch");
    } while (marked[0] == BATCH);
    return sent;
  }
}
