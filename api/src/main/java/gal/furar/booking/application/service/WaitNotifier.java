// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingSlotReleased;
import gal.furar.booking.BookingWaitsCancelled;
import gal.furar.booking.application.port.out.BookingWaits;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.booking.domain.SlotGrid.SlotState;
import gal.furar.booking.domain.Wait;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * O que lles pasa ás esperas cando cambia a ocupación ou a instalación (ADR-0033, puntos 5 e 7).
 * Chámano os casos de uso que escriben, na súa transacción, baixo o bloqueo da instalación e
 * despois de escribir:
 *
 * <ul>
 *   <li>{@link #served}: un correo conseguiu reserva nunha franxa; a súa espera por ela bórrase.
 *   <li>{@link #gone}: un cambio da instalación deixa horas sen existir; as súas esperas bórranse,
 *       sen correo e sen reconto.
 *   <li>{@link #released}: soltouse ocupación; por cada franxa esperada que pasa de ocupada a libre
 *       publícase un {@link BookingSlotReleased}, sen datos de ninguén. Sen esperas, unha consulta
 *       por índice e nada máis.
 * </ul>
 */
@Component
class WaitNotifier {

  /** A acción do rexistro de actividade: un cambio da instalación borrou esperas. */
  static final String WAITS_REMOVED = "booking.waits-removed";

  /** «Para sempre», ao ler todas as esperas activas dunha instalación. */
  private static final Duration HORIZON = Duration.ofDays(3660);

  private final BookingWaits waits;
  private final FacilityQueries facilities;
  private final SlotGrids grids;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  WaitNotifier(
      BookingWaits waits,
      FacilityQueries facilities,
      SlotGrids grids,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.waits = waits;
    this.facilities = facilities;
    this.grids = grids;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  /** O que ocupa unha reserva confirmada, tal como o le a grella. */
  static Occupied occupied(Booking booking) {
    BookedSlot slot = booking.slot();
    return new Occupied(slot.useId(), slot.partId(), slot.start(), slot.end(), booking.seats());
  }

  /**
   * Ese correo ten agora unha reserva confirmada nesa franxa, por calquera vía (internet, mostrador
   * ou un cambio): a espera está atendida e bórrase, sen reconto.
   */
  void served(@Nullable String emailKey, BookedSlot slot) {
    if (emailKey != null) {
      waits.deleteServed(emailKey, slot.useId(), slot.start(), slot.end());
    }
  }

  /**
   * Borra as esperas activas que o cambio da instalación deixa fóra (PC14): as que pisan as horas
   * perdidas, as do uso cambiado ou arquivado, as dun uso nos días nos que deixa de valer, as dos
   * outros usos nos días que un uso pasa a bloquear ou todas as dunha instalación pechada ou de
   * baixa. Sen reconto. Cando a instalación enteira deixa de reservarse por internet (quitala,
   * pechar ou dar de baixa), cada persoa recibe un correo ({@link BookingWaitsCancelled}); nos
   * demais cambios, sen correo.
   *
   * <p>Se borra algunha, queda un evento no rexistro de actividade ({@value #WAITS_REMOVED}) só con
   * cantas eran e o tipo de cambio, sen ningún dato de quen esperaba: quen bloquea, pecha ou cambia
   * o horario (tamén a plataforma, que non decide sobre reservas) deixa rastro do que fixo sobre as
   * esperas. Sen esperas borradas non se rexistra nada.
   */
  void gone(FacilityChange change, Instant now) {
    if (change instanceof FacilityChange.SeatsChanged) {
      return;
    }
    Predicate<Wait> lost =
        switch (change) {
          case FacilityChange.Closure c -> w -> true;
          case FacilityChange.Withdrawal c -> w -> true;
          case FacilityChange.OnlineBookingOff c -> w -> true;
          case FacilityChange.Block b -> w -> w.overlaps(b.start(), b.end());
          case FacilityChange.UseChanged u -> w -> w.useId().equals(u.useId());
          case FacilityChange.UseArchived u -> w -> w.useId().equals(u.useId());
          case FacilityChange.UseDatesChanged u -> outOfDates(u.useId());
          case FacilityChange.SeatsChanged s -> w -> false;
          case FacilityChange.ScheduleChanged s ->
              w -> s.lost().stream().anyMatch(i -> w.overlaps(i.start(), i.end()));
          case FacilityChange.ExclusiveUse e -> w -> blockedBy(e, w);
        };
    Instant from = now;
    Instant to = now.plus(HORIZON);
    if (change instanceof FacilityChange.Block b) {
      from = b.start();
      to = b.end();
    } else if (change instanceof FacilityChange.ScheduleChanged s) {
      if (s.lost().isEmpty()) {
        return;
      }
      from = s.lost().stream().map(OpeningInterval::start).min(Instant::compareTo).orElseThrow();
      to = s.lost().stream().map(OpeningInterval::end).max(Instant::compareTo).orElseThrow();
    }
    List<UUID> ids =
        waits.activeOfFacility(change.facilityId(), from, to, now).stream()
            .filter(lost)
            .map(Wait::id)
            .toList();
    if (ids.isEmpty()) {
      return;
    }
    int removed;
    if (tellsWaiting(change)) {
      // A instalación enteira deixa de reservarse por internet: cada persoa recibe un correo
      // despois do commit, un só con todas as súas esperas. O evento non leva a ninguén.
      UUID cancellation = Ids.newV7();
      removed = waits.cancel(ids, cancellation, now);
      if (removed > 0) {
        events.publishEvent(
            new BookingWaitsCancelled(
                TenantContext.require().id(),
                cancellation,
                change.facilityId(),
                now.truncatedTo(ChronoUnit.MICROS)));
      }
    } else {
      removed = waits.deleteAll(ids);
    }
    if (removed > 0) {
      TenantRef tenant = TenantContext.require();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", change.facilityId().toString());
      details.put("facilityChange", change.getClass().getSimpleName());
      details.put("count", removed);
      audit.record(
          new AuditEvent(WAITS_REMOVED, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
    }
  }

  /**
   * Os cambios que deixan a instalación enteira sen reserva por internet (quitala, pechar ou dar de
   * baixa): a quen esperaba escríbeselle (ADR-0033, revisión do 2026-11-11). Os demais (un bloqueo,
   * o horario, un uso) seguen borrando sen correo (PC14).
   */
  static boolean tellsWaiting(FacilityChange change) {
    return change instanceof FacilityChange.Closure
        || change instanceof FacilityChange.Withdrawal
        || change instanceof FacilityChange.OnlineBookingOff;
  }

  /** As esperas dese uso por un día no que, tal como queda gardado, xa non vale. */
  private Predicate<Wait> outOfDates(UUID useId) {
    Optional<UseSummary> use = facilities.use(useId);
    return w ->
        w.useId().equals(useId)
            && use.map(u -> !u.availableOn(w.start().atZone(SlotGrid.ZONE).toLocalDate()))
                .orElse(true);
  }

  private static boolean blockedBy(FacilityChange.ExclusiveUse change, Wait wait) {
    if (wait.useId().equals(change.useId())) {
      return false;
    }
    LocalDate date = wait.start().atZone(SlotGrid.ZONE).toLocalDate();
    return (change.weekdays() & (1 << (date.getDayOfWeek().getValue() - 1))) != 0
        && change.dates().stream().anyMatch(d -> !date.isBefore(d.from()) && !date.isAfter(d.to()));
  }

  /**
   * Soltouse ocupación na instalación: {@code released} é o que se soltou e {@code written} o que
   * se escribiu na mesma transacción (ao cambiar unha reserva). Avisa só das franxas esperadas que
   * pasan de ocupadas a libres.
   */
  void released(UUID facilityId, List<Occupied> released, List<Occupied> written) {
    if (released.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    Instant from = released.stream().map(Occupied::start).min(Instant::compareTo).orElseThrow();
    Instant to = released.stream().map(Occupied::end).max(Instant::compareTo).orElseThrow();
    // 1. Unha consulta por índice; sen esperas, remata aquí (o caso normal).
    List<Wait> waiting =
        waits.activeOfFacility(facilityId, from, to, now).stream()
            .filter(w -> released.stream().anyMatch(o -> w.overlaps(o.start(), o.end())))
            .toList();
    if (waiting.isEmpty()) {
      return;
    }
    Map<UUID, BookableUse> uses =
        facilities.usesById(waiting.stream().map(Wait::useId).collect(Collectors.toSet())).stream()
            .filter(u -> !u.archived())
            .collect(Collectors.toMap(UseSummary::id, Uses::bookable));
    Map<LocalDate, List<Wait>> byDate =
        waiting.stream()
            .collect(Collectors.groupingBy(w -> w.start().atZone(SlotGrid.ZONE).toLocalDate()));
    UUID municipality = TenantContext.require().id();
    Instant releasedAt = now.truncatedTo(ChronoUnit.MICROS);
    byDate.forEach(
        (date, sameDay) -> {
          // 2. A ocupación do día como queda, e a de antes reconstruída en memoria.
          SlotGrid after = grids.of(facilityId, date, now);
          for (Freed slot : freed(after, released, written, uses, sameDay)) {
            events.publishEvent(
                new BookingSlotReleased(
                    municipality,
                    Ids.newV7(),
                    facilityId,
                    slot.useId(),
                    slot.start(),
                    slot.end(),
                    releasedAt));
          }
        });
  }

  /**
   * As franxas esperadas que pasan de ocupadas a libres (ADR-0033, punto 7): a grella de antes é a
   * de agora, menos o escrito, máis o soltado. Nun uso por prazas ou por partes, unha segunda
   * cancelación cando a hora xa tiña sitio non cambia nada; un cambio que deixa a hora igual de
   * chea, tampouco. Cobre a hora que ocupaba outro uso. Unha vez por uso e franxa.
   */
  static Set<Freed> freed(
      SlotGrid after,
      Collection<Occupied> released,
      Collection<Occupied> written,
      Map<UUID, BookableUse> uses,
      Collection<Wait> waiting) {
    List<Occupied> occupancy = new ArrayList<>(after.occupancy());
    written.forEach(occupancy::remove);
    occupancy.addAll(released);
    SlotGrid before =
        new SlotGrid(
            after.date(), after.intervals(), occupancy, after.exclusiveUseId(), after.now());
    Set<Freed> result = new LinkedHashSet<>();
    for (Wait wait : waiting) {
      BookableUse use = uses.get(wait.useId());
      if (use == null || !use.rules().allows(wait.start(), after.now())) {
        continue;
      }
      if (before.stateAt(use, wait.start()) == SlotState.BUSY
          && after.stateAt(use, wait.start()) == SlotState.FREE) {
        result.add(new Freed(wait.useId(), wait.start(), wait.end()));
      }
    }
    return result;
  }

  /** Unha franxa dun uso que quedou libre. */
  record Freed(UUID useId, Instant start, Instant end) {}
}
