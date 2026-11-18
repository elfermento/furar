// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSession;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.event.domain.WaitlistNotice;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Os eventos, as inscricións e as sesións en memoria, dun só concello. */
final class InMemoryEvents implements EventRepository, RegistrationRepository, SessionRepository {

  final Map<UUID, Event> events = new LinkedHashMap<>();
  final Map<UUID, Registration> registrations = new LinkedHashMap<>();
  final Map<UUID, EventSession> sessions = new LinkedHashMap<>();
  final Map<UUID, Map<UUID, Boolean>> marks = new HashMap<>();
  final Map<UUID, Saved> saved = new HashMap<>();
  int locks;

  // --- EventRepository ---

  @Override
  public Optional<Event> findById(EventId id) {
    return Optional.ofNullable(events.get(id.value()));
  }

  @Override
  public List<Event> findAllById(Collection<EventId> ids) {
    return ids.stream().map(id -> events.get(id.value())).filter(Objects::nonNull).toList();
  }

  @Override
  public List<Event> findAll() {
    return events.values().stream()
        .sorted(Comparator.comparing(e -> e.details().startsAt()))
        .toList();
  }

  @Override
  public List<Event> findVisibleFrom(Instant now) {
    return findAll().stream()
        .filter(e -> e.visible() && e.details().endsAt().isAfter(now))
        .toList();
  }

  @Override
  public Event insert(Event event) {
    Event saved =
        Event.rehydrate(
            event.id(),
            event.details(),
            event.cancelledAt(),
            event.endedFrom(),
            0,
            event.createdAt(),
            event.updatedAt());
    events.put(event.id().value(), saved);
    return saved;
  }

  @Override
  public Event update(Event event) {
    Event current = events.get(event.id().value());
    if (current == null || !Objects.equals(current.version(), event.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Event saved =
        Event.rehydrate(
            event.id(),
            event.details(),
            event.cancelledAt(),
            event.endedFrom(),
            Objects.requireNonNull(event.version()) + 1,
            event.createdAt(),
            event.updatedAt());
    events.put(event.id().value(), saved);
    return saved;
  }

  @Override
  public void delete(EventId id) {
    events.remove(id.value());
    sessions.values().removeIf(s -> s.eventId().equals(id));
  }

  // --- RegistrationRepository ---

  @Override
  public boolean lockEvent(EventId id) {
    locks++;
    return events.containsKey(id.value());
  }

  @Override
  public Map<UUID, Counts> counts(Collection<EventId> ids) {
    Map<UUID, Counts> out = new HashMap<>();
    for (EventId id : ids) {
      int[] c = new int[4];
      for (Registration r : registrations.values()) {
        if (r.eventId().equals(id)) {
          c[0] += r.status() == RegistrationStatus.CONFIRMED ? 1 : 0;
          c[1] += r.status() == RegistrationStatus.PENDING ? 1 : 0;
          c[2] += r.status() == RegistrationStatus.NOTIFIED ? 1 : 0;
          c[3] += r.status() == RegistrationStatus.WAITING ? 1 : 0;
        }
      }
      if (c[0] + c[1] + c[2] + c[3] > 0) {
        out.put(id.value(), new Counts(c[0], c[1], c[2], c[3]));
      }
    }
    return out;
  }

  @Override
  public Optional<Registration> find(RegistrationId id) {
    return Optional.ofNullable(registrations.get(id.value()));
  }

  @Override
  public Optional<Registration> findByPerson(
      EventId event, String subject, @Nullable UUID dependentId) {
    return registrations.values().stream()
        .filter(r -> r.eventId().equals(event) && subject.equals(r.participant().subject()))
        .filter(r -> Objects.equals(r.dependent() == null ? null : r.dependent().id(), dependentId))
        .findFirst();
  }

  @Override
  public List<Registration> listByAccount(EventId event, String subject) {
    return listForEvent(event).stream()
        .filter(r -> subject.equals(r.participant().subject()))
        .toList();
  }

  @Override
  public List<Registration> listForEvent(EventId event) {
    return registrations.values().stream().filter(r -> r.eventId().equals(event)).toList();
  }

  @Override
  public List<Registration> waitlist(EventId event) {
    return listForEvent(event).stream().filter(r -> r.status().waitlisted()).toList();
  }

  @Override
  public List<Registration> listForPerson(String subject) {
    return registrations.values().stream()
        .filter(r -> subject.equals(r.participant().subject()))
        .filter(r -> r.status() != RegistrationStatus.REJECTED)
        .toList();
  }

  @Override
  public long countForEvent(EventId event) {
    return listForEvent(event).size();
  }

  @Override
  public long countPendingFrom(Instant now) {
    return registrations.values().stream()
        .filter(r -> r.status() == RegistrationStatus.PENDING)
        .filter(
            r -> {
              Event e = events.get(r.eventId().value());
              return e != null && !e.cancelled() && e.details().startsAt().isAfter(now);
            })
        .count();
  }

  @Override
  public int countActiveWaits(String subject, Instant now) {
    return (int)
        registrations.values().stream()
            .filter(r -> subject.equals(r.participant().subject()))
            .filter(r -> r.status().waitlisted())
            .filter(
                r -> {
                  Event e = events.get(r.eventId().value());
                  return e != null && !e.cancelled() && e.details().closesAt().isAfter(now);
                })
            .count();
  }

  @Override
  public List<Registration> noticesToMail(Instant now) {
    return registrations.values().stream()
        .filter(r -> r.status() == RegistrationStatus.NOTIFIED)
        .filter(r -> r.notice() != null && r.notice().mailedAt() == null)
        .filter(r -> !r.notice().notifiedAt().isAfter(now))
        .toList();
  }

  @Override
  public void markNoticeMailed(RegistrationId id, Instant at) {
    Registration r = registrations.get(id.value());
    WaitlistNotice n = r == null ? null : r.notice();
    if (r == null || n == null) {
      return;
    }
    registrations.put(
        id.value(),
        new Registration(
            r.id(),
            r.eventId(),
            r.status(),
            r.participant(),
            r.dependent(),
            r.birthYear(),
            r.birthDate(),
            r.payment(),
            r.decidedAt(),
            new WaitlistNotice(n.notifiedAt(), n.exclusiveUntil(), at),
            r.coveredAt(),
            r.createdAt(),
            r.updatedAt(),
            r.version()));
  }

  @Override
  public List<EventId> eventsWithWaiting(Instant now) {
    return registrations.values().stream()
        .filter(r -> r.status().waitlisted())
        .map(Registration::eventId)
        .distinct()
        .toList();
  }

  @Override
  public int deleteClosedWaits(Instant now, Instant cancelledBefore) {
    return 0;
  }

  @Override
  public Registration insert(Registration registration) {
    UUID dependent = registration.dependent() == null ? null : registration.dependent().id();
    if (registration.participant().subject() != null
        && findByPerson(registration.eventId(), registration.participant().subject(), dependent)
            .isPresent()) {
      throw new DomainException(EventError.ALREADY_REGISTERED);
    }
    registrations.put(registration.id().value(), registration);
    return registration;
  }

  @Override
  public Registration update(Registration r) {
    Registration current = registrations.get(r.id().value());
    if (current == null || current.version() != r.version()) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Registration saved =
        new Registration(
            r.id(),
            r.eventId(),
            r.status(),
            r.participant(),
            r.dependent(),
            r.birthYear(),
            r.birthDate(),
            r.payment(),
            r.decidedAt(),
            r.notice(),
            r.coveredAt(),
            r.createdAt(),
            r.updatedAt(),
            r.version() + 1);
    registrations.put(r.id().value(), saved);
    return saved;
  }

  @Override
  public void delete(RegistrationId id) {
    registrations.remove(id.value());
    marks.values().forEach(m -> m.remove(id.value()));
  }

  @Override
  public int anonymizeEndedBefore(Instant cutoff, Instant now) {
    return 0;
  }

  // --- SessionRepository ---

  @Override
  public void insertAll(List<EventSession> list) {
    list.forEach(s -> sessions.put(s.id(), s));
  }

  @Override
  public List<EventSession> sessionsOf(EventId event) {
    return sessions.values().stream()
        .filter(s -> s.eventId().equals(event))
        .sorted(Comparator.comparing(EventSession::date))
        .toList();
  }

  @Override
  public Optional<EventSession> find(EventId event, UUID sessionId) {
    return Optional.ofNullable(sessions.get(sessionId)).filter(s -> s.eventId().equals(event));
  }

  @Override
  public List<EventSession> upcoming(Collection<EventId> list, Instant now) {
    return sessions.values().stream()
        .filter(s -> list.contains(s.eventId()) && !s.cancelled() && s.endsAt().isAfter(now))
        .sorted(Comparator.comparing(EventSession::startsAt))
        .toList();
  }

  @Override
  public Map<UUID, UUID> byBookings(Collection<UUID> bookingIds) {
    Map<UUID, UUID> out = new HashMap<>();
    sessions.values().stream()
        .filter(s -> s.bookingId() != null && bookingIds.contains(s.bookingId()))
        .forEach(s -> out.put(s.bookingId(), s.id()));
    return out;
  }

  @Override
  public Optional<EventSession> findByBooking(UUID bookingId) {
    return sessions.values().stream().filter(s -> bookingId.equals(s.bookingId())).findFirst();
  }

  @Override
  public void cancel(Collection<UUID> sessionIds, Instant now) {
    for (UUID id : sessionIds) {
      EventSession s = sessions.get(id);
      if (s != null && !s.cancelled()) {
        sessions.put(id, s.cancel(now));
      }
    }
  }

  @Override
  public Map<UUID, Boolean> marks(UUID sessionId) {
    return new HashMap<>(marks.getOrDefault(sessionId, Map.of()));
  }

  @Override
  public void mark(
      UUID sessionId, Map<UUID, @Nullable Boolean> given, Instant now, @Nullable String byName) {
    saved.put(sessionId, new Saved(now, byName));
    Map<UUID, Boolean> m = marks.computeIfAbsent(sessionId, k -> new HashMap<>());
    given.forEach(
        (r, p) -> {
          if (p == null) {
            m.remove(r);
          } else {
            m.put(r, p);
          }
        });
    EventSession s = sessions.get(sessionId);
    if (s != null && s.attendanceTakenAt() == null) {
      sessions.put(
          sessionId,
          new EventSession(
              s.id(),
              s.eventId(),
              s.date(),
              s.startsAt(),
              s.endsAt(),
              s.bookingId(),
              s.cancelledAt(),
              now));
    }
  }

  @Override
  public Optional<Saved> saved(UUID sessionId) {
    return Optional.ofNullable(saved.get(sessionId));
  }

  @Override
  public List<Today> on(LocalDate date) {
    List<Today> out = new ArrayList<>();
    for (EventSession s : sessions.values()) {
      Event e = events.get(s.eventId().value());
      if (s.date().equals(date) && !s.cancelled() && e != null && !e.cancelled()) {
        out.add(
            new Today(
                e.id(),
                e.details().name().gl(),
                e.details().name().es(),
                e.details().place().facilityId(),
                e.details().place().other(),
                s,
                0));
      }
    }
    return out;
  }

  @Override
  public Map<UUID, Tally> tallies(EventId event) {
    Map<UUID, Tally> out = new HashMap<>();
    for (EventSession s : sessionsOf(event)) {
      Map<UUID, Boolean> m = marks.getOrDefault(s.id(), Map.of());
      int present = (int) m.values().stream().filter(Boolean::booleanValue).count();
      out.put(s.id(), new Tally(present, m.size() - present));
    }
    return out;
  }

  @Override
  public Map<UUID, Summary> summary(EventId event) {
    List<EventSession> taken =
        sessionsOf(event).stream()
            .filter(s -> !s.cancelled() && s.attendanceTakenAt() != null)
            .toList();
    Map<UUID, int[]> counts = new HashMap<>();
    for (EventSession s : taken) {
      marks
          .getOrDefault(s.id(), Map.of())
          .forEach(
              (r, p) -> {
                int[] c = counts.computeIfAbsent(r, k -> new int[2]);
                c[p ? 0 : 1]++;
              });
    }
    Map<UUID, Summary> out = new HashMap<>();
    counts.forEach((r, c) -> out.put(r, new Summary(taken.size(), c[0], c[1])));
    return out;
  }
}
