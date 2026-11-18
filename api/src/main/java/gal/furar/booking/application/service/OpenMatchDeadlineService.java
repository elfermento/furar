// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.OpenMatchCancelled;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchCancelReason;
import gal.furar.booking.domain.OpenMatchStatus;
import gal.furar.facility.FacilityQueries;
import gal.furar.municipality.MunicipalityOpenMatchPolicy;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O prazo das partidas abertas (ADR-0040, punto 7). Cada 5 minutos, en cada concello coa función
 * acesa e coa súa RLS, busca sen bloqueos as partidas abertas, sen ocultar e sen comprobar cuxo
 * prazo ({@code inicio − horas}) xa pasou e é posterior ao último cambio da política (acender ou
 * subir as horas nunca cancela en bloque). Cada unha vai na súa transacción curta: bloqueo da
 * instalación → fila da reserva → fila da partida, e volve comprobar todo. Co mínimo cuberto, marca
 * a partida e xa non a mira; sen el, cancela a reserva sen custo (avisa a quen espera pola hora) e
 * a partida, e publica {@link OpenMatchCancelled}: o correo de quen organiza é o da partida, non o
 * da reserva.
 *
 * <p>Idempotente e segura con varias instancias: a segunda agarda polo bloqueo e atópaa resolta. Un
 * fallo nunha partida non para as demais. O rexistro técnico só leva o concello e cantas.
 */
@Service
@PlatformTask
public class OpenMatchDeadlineService {

  /** Partidas como moito por pasada e concello. */
  static final int BATCH = 200;

  private static final Logger LOG = LoggerFactory.getLogger(OpenMatchDeadlineService.class);

  private final MunicipalityDirectory municipalities;
  private final MunicipalityQueries policies;
  private final OpenMatches matches;
  private final BookingRepository bookings;
  private final FacilityQueries facilities;
  private final WaitNotifier waitNotifier;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;
  private final Clock clock;

  public OpenMatchDeadlineService(
      MunicipalityDirectory municipalities,
      MunicipalityQueries policies,
      OpenMatches matches,
      BookingRepository bookings,
      FacilityQueries facilities,
      WaitNotifier waitNotifier,
      ApplicationEventPublisher events,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.municipalities = municipalities;
    this.policies = policies;
    this.matches = matches;
    this.bookings = bookings;
    this.facilities = facilities;
    this.waitNotifier = waitNotifier;
    this.events = events;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Scheduled(cron = "${furar.booking.open-match-deadline-cron:0 */5 * * * *}")
  void scheduled() {
    apply();
  }

  /** Mira o prazo en todos os concellos e devolve cantas partidas cancelou. */
  public int apply() {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    int total = 0;
    for (TenantRef municipality : all) {
      try {
        int cancelled = TenantContext.runAs(municipality, () -> municipality(now));
        if (cancelled > 0) {
          LOG.info(
              "open match deadline municipality={} cancelled={}", municipality.id(), cancelled);
        }
        total += cancelled;
      } catch (RuntimeException e) {
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "open match deadline failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    return total;
  }

  private int municipality(Instant now) {
    Optional<MunicipalityOpenMatchPolicy> found =
        policies.openMatchPolicy(TenantContext.require().id());
    if (found.isEmpty() || !found.get().enabled()) {
      // Apagada: as partidas quedan como estaban e ningunha se cancela.
      return 0;
    }
    MunicipalityOpenMatchPolicy policy = found.get();
    Instant from = Objects.requireNonNull(policy.deadlinesFrom(), "from");
    List<UUID> due =
        Objects.requireNonNull(
            tx.execute(s -> matches.deadlineDue(now, from, policy.cancelHours(), BATCH)), "due");
    int cancelled = 0;
    for (UUID id : due) {
      try {
        if (Boolean.TRUE.equals(tx.execute(s -> one(id, policy, from, now)))) {
          cancelled++;
        }
      } catch (RuntimeException e) {
        LOG.warn("open match deadline failed match={} error={}", id, e.getClass().getName());
      }
    }
    return cancelled;
  }

  /** Unha partida, baixo os seus bloqueos; verdadeiro se a cancelou. */
  private boolean one(UUID id, MunicipalityOpenMatchPolicy policy, Instant from, Instant now) {
    Optional<OpenMatches.Listed> seen = matches.listed(id);
    if (seen.isEmpty() || !facilities.lockFacility(seen.get().facilityId())) {
      return false;
    }
    UUID bookingId = seen.get().match().bookingId();
    Optional<Booking> booking = bookings.findLocked(new BookingId(bookingId));
    Optional<OpenMatch> locked = matches.findLocked(id);
    if (booking.isEmpty() || locked.isEmpty()) {
      return false;
    }
    Booking b = booking.get();
    OpenMatch m = locked.get();
    Instant deadline = OpenMatch.deadline(b.slot().start(), policy.cancelHours());
    // Volve comprobar: se mentres tanto a cancelou, pechou, ocultou ou moveu alguén, nada.
    if (m.status() != OpenMatchStatus.OPEN
        || m.hidden()
        || m.deadlineCheckedAt() != null
        || b.status() != BookingStatus.CONFIRMED
        || !b.slot().start().isAfter(now)
        || deadline.isAfter(now)
        || deadline.isBefore(from)) {
      return false;
    }
    int confirmed = matches.listed(id).orElseThrow().confirmed();
    if (m.filled(confirmed)) {
      matches.update(m, m.deadlineMet(now));
      return false;
    }
    Booking after = b.cancelUnfilledMatch(now);
    bookings.cancel(b, after, Booking.OPEN_MATCH_ACTOR, Set.of(BookingCancelled.Channel.EMAIL));
    waitNotifier.released(b.slot().facilityId(), List.of(WaitNotifier.occupied(b)), List.of());
    matches.update(m, m.cancel(OpenMatchCancelReason.UNFILLED, now));
    matches.deletePending(m.id());
    events.publishEvent(new OpenMatchCancelled(TenantContext.require().id(), m.id(), Ids.newV7()));
    return true;
  }
}
