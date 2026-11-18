// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.CreateSeriesCommand;
import gal.furar.booking.application.port.in.SeriesConflictsChangedException;
import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlan;
import gal.furar.booking.domain.season.SeriesPlanner;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.facility.FacilityDay;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.DomainException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Calcula e escribe unha tempada (ADR-0039, punto 8). O cálculo le o calendario da instalación e as
 * ocupacións cun número fixo de lecturas; a escritura vai en lote (a serie, as reservas, as
 * ocupacións e a historia) baixo o bloqueo da instalación que xa tomou quen chama.
 */
@Component
class SeriesWriter {

  /** Intentos de códigos: con 32⁴ códigos, chocar dúas veces seguidas xa é case imposible. */
  private static final int CODE_ATTEMPTS = 10;

  private final FacilityQueries facilities;
  private final SeriesStore store;
  private final SeasonSupport support;
  private final RandomGenerator random = new SecureRandom();

  SeriesWriter(FacilityQueries facilities, SeriesStore store, SeasonSupport support) {
    this.facilities = facilities;
    this.store = store;
    this.support = support;
  }

  /** As sesións e os días que non se crean, sen escribir. */
  SeriesPlan plan(SeriesSchedule schedule, UseSummary use, Instant now) {
    BookableUse bookable = support.bookable(use);
    List<SeriesPlanner.Day> days =
        facilities
            .calendar(schedule.facilityId(), schedule.firstDate(), schedule.lastDate())
            .stream()
            .map(SeriesWriter::day)
            .toList();
    Instant from = schedule.start(schedule.firstDate()).toInstant();
    Instant to = schedule.end(schedule.lastDate()).toInstant().plus(Duration.ofDays(1));
    List<SeriesPlanner.Occupant> occupants =
        store.occupants(schedule.facilityId(), from, to.isAfter(from) ? to : from.plusSeconds(1));
    Map<UUID, String> useNames =
        support.useNames(
            days.stream()
                .map(SeriesPlanner.Day::exclusiveUseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return SeriesPlanner.plan(schedule, bookable, days, occupants, useNames, now);
  }

  /** O que xa se creou con esta clave: coa mesma pegada, esa; con outra, 422. */
  Optional<Series> remembered(CreateSeriesCommand command) {
    Optional<Series> found = store.findByKey(command.key().value());
    if (found.isPresent() && !found.get().fingerprint().equals(command.fingerprint())) {
      throw new DomainException(BookingError.IDEMPOTENCY_KEY_REUSED);
    }
    return found;
  }

  /**
   * Volve calcular baixo o bloqueo e, se os días con choque son os que viu o xestor, escribe a
   * serie e as súas sesións libres.
   *
   * @throws SeriesConflictsChangedException se non o son, coa vista previa nova
   */
  Written create(
      Organization organization,
      CreateSeriesCommand command,
      UseSummary use,
      @Nullable UUID requestId,
      String actor,
      Instant now) {
    SeriesSchedule schedule = command.schedule();
    SeriesPlan plan = plan(schedule, use, now);
    if (!plan.conflictDates().equals(command.skip())) {
      throw new SeriesConflictsChangedException(plan);
    }
    BookableUse bookable = support.bookable(use);
    Series series =
        new Series(
            UUID.randomUUID(),
            organization.id(),
            schedule,
            bookable.rentalMode(),
            null,
            Series.PaymentStatus.initial(Uses.unitPrice(use)),
            requestId,
            command.key().value(),
            command.fingerprint(),
            actor,
            null,
            now,
            now);
    store.insert(series);
    Holder holder = Booking.organizationHolder(organization.name(), support.language());
    List<Booking> pending = new ArrayList<>();
    for (SeriesPlan.Session s : plan.sessions()) {
      BookedSlot slot = slot(schedule, bookable, s);
      pending.add(
          Booking.createSession(
              BookingId.newId(),
              BookingCode.random(random),
              slot,
              holder,
              organization.id(),
              series.id(),
              actor,
              now));
    }
    write(pending, actor);
    return new Written(series, plan);
  }

  /**
   * Escribe as sesións en lote (as dunha tempada ou dunha actividade do concello, ADR-0041); as que
   * chocan nun código volven escribirse con outro.
   */
  void write(List<Booking> sessions, String actor) {
    List<Booking> pending = sessions;
    for (int attempt = 1; attempt <= CODE_ATTEMPTS && !pending.isEmpty(); attempt++) {
      pending = store.insertSessions(freshCodes(pending), actor);
    }
    if (!pending.isEmpty()) {
      throw new IllegalStateException("No free booking codes after " + CODE_ATTEMPTS + " attempts");
    }
  }

  /** As franxas que ocupa cada sesión libre dun plan, co número de franxas do uso. */
  static BookedSlot slot(SeriesSchedule schedule, BookableUse bookable, SeriesPlan.Session s) {
    long minutes = Duration.between(s.start(), s.end()).toMinutes();
    int slots = (int) Math.max(1, (minutes + bookable.slotMinutes() - 1) / bookable.slotMinutes());
    return new BookedSlot(
        schedule.facilityId(),
        schedule.useId(),
        schedule.partId(),
        bookable.rentalMode(),
        s.start(),
        s.end(),
        slots);
  }

  /** A serie escrita e o que se calculou baixo o bloqueo. */
  record Written(Series series, SeriesPlan plan) {}

  /**
   * Códigos que aínda ninguén ten no concello (unha consulta) e distintos entre si; os que chocan,
   * outra vez.
   */
  private List<Booking> freshCodes(List<Booking> sessions) {
    List<Booking> result = new ArrayList<>(sessions);
    for (int round = 0; round < CODE_ATTEMPTS; round++) {
      Set<String> seen = new HashSet<>();
      Set<String> taken = store.takenCodes(result.stream().map(b -> b.code().value()).toList());
      boolean clean = true;
      for (int i = 0; i < result.size(); i++) {
        Booking b = result.get(i);
        if (taken.contains(b.code().value()) || !seen.add(b.code().value())) {
          clean = false;
          result.set(i, recoded(b));
        }
      }
      if (clean) {
        return result;
      }
    }
    return result;
  }

  private Booking recoded(Booking b) {
    return b.withCode(BookingCode.random(random));
  }

  private static SeriesPlanner.Day day(FacilityDay d) {
    return new SeriesPlanner.Day(
        d.date(),
        d.holiday(),
        d.open().stream().map(o -> new SeriesPlanner.Span(o.start(), o.end())).toList(),
        d.blocks().stream()
            .map(b -> new SeriesPlanner.Block(b.start(), b.end(), b.reason()))
            .toList(),
        d.exclusiveUseId());
  }
}
