// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.RegistrationRepository.Counts;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventSession;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Monta as vistas dos eventos cun número fixo de lecturas (sen N+1): os números de persoas, as
 * instalacións e as súas portadas e, nas actividades con horario, o uso, a parte e as próximas
 * sesións. Dentro da transacción de quen chama.
 */
@Component
class EventViews {

  private final RegistrationRepository registrations;
  private final FacilityQueries facilities;
  private final SessionRepository sessions;
  private final Clock clock;

  EventViews(
      RegistrationRepository registrations,
      FacilityQueries facilities,
      SessionRepository sessions,
      Clock clock) {
    this.registrations = registrations;
    this.facilities = facilities;
    this.sessions = sessions;
    this.clock = clock;
  }

  EventView of(Event event) {
    return of(List.of(event)).getFirst();
  }

  List<EventView> of(List<Event> events) {
    if (events.isEmpty()) {
      return List.of();
    }
    Map<UUID, Counts> counts = registrations.counts(events.stream().map(Event::id).toList());
    List<UUID> places =
        events.stream()
            .map(e -> e.details().place().facilityId())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, FacilitySummary> summaries =
        places.isEmpty()
            ? Map.of()
            : facilities.summary(places).stream()
                .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    Map<UUID, FacilityCover> covers =
        places.isEmpty()
            ? Map.of()
            : facilities.covers(places).stream()
                .collect(
                    Collectors.toMap(FacilityCover::facilityId, Function.identity(), (a, b) -> a));
    List<Event> scheduled = events.stream().filter(e -> e.details().scheduled()).toList();
    Map<UUID, List<EventSession>> next = next(scheduled);
    List<UUID> useIds =
        scheduled.stream()
            .map(e -> e.details().schedule())
            .filter(Objects::nonNull)
            .map(EventSchedule::useId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, UseSummary> uses =
        useIds.isEmpty()
            ? Map.of()
            : facilities.usesById(useIds).stream()
                .collect(Collectors.toMap(UseSummary::id, Function.identity()));
    return events.stream()
        .map(
            e -> {
              Counts c = counts.getOrDefault(e.id().value(), Counts.NONE);
              UUID place = e.details().place().facilityId();
              EventSchedule schedule = e.details().schedule();
              UseSummary use =
                  schedule == null || schedule.useId() == null ? null : uses.get(schedule.useId());
              return new EventView(
                  e,
                  c.confirmed(),
                  c.pending(),
                  c.notified(),
                  c.waiting(),
                  place == null ? null : summaries.get(place),
                  place == null ? null : covers.get(place),
                  use == null ? null : use.name(),
                  use == null || schedule.partId() == null
                      ? null
                      : partName(use, schedule.partId()),
                  next.getOrDefault(e.id().value(), List.of()));
            })
        .toList();
  }

  /** As próximas sesións de cada actividade, como moito {@link EventView#NEXT_SESSIONS}. */
  private Map<UUID, List<EventSession>> next(List<Event> scheduled) {
    if (scheduled.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<EventSession>> next = new HashMap<>();
    List<EventId> ids = scheduled.stream().map(Event::id).toList();
    for (EventSession s : sessions.upcoming(ids, clock.instant())) {
      List<EventSession> list = next.computeIfAbsent(s.eventId().value(), k -> new ArrayList<>());
      if (list.size() < EventView.NEXT_SESSIONS) {
        list.add(s);
      }
    }
    return next;
  }

  private static @Nullable String partName(UseSummary use, UUID partId) {
    return use.parts().stream()
        .filter(p -> p.id().equals(partId))
        .map(UseSummary.Part::name)
        .findFirst()
        .orElse(null);
  }

  /** Por id, os que existan. */
  Map<UUID, EventView> byId(List<Event> events) {
    return of(events).stream()
        .collect(Collectors.toMap(v -> v.event().id().value(), Function.identity()));
  }
}
