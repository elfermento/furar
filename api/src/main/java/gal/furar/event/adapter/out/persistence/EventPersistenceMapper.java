// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import gal.furar.event.domain.BirthYears;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventDetails;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventKind;
import gal.furar.event.domain.EventPlace;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventText;
import gal.furar.event.domain.PricePeriod;
import java.time.DayOfWeek;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** Dominio ↔ entidade JPA, a man. */
final class EventPersistenceMapper {

  private EventPersistenceMapper() {}

  static EventEntity newEntity(Event event) {
    var entity = new EventEntity(event.id().value(), event.createdAt());
    apply(entity, event);
    return entity;
  }

  static void apply(EventEntity entity, Event event) {
    EventDetails d = event.details();
    EventText description = d.description();
    entity.apply(
        d.name().gl(),
        d.name().es(),
        description == null ? null : description.gl(),
        description == null ? null : description.es(),
        d.startsAt(),
        d.place().facilityId(),
        d.place().other(),
        d.capacity(),
        d.priceCents(),
        d.registrationDeadline(),
        d.published(),
        d.approval(),
        event.cancelledAt(),
        event.updatedAt());
    EventSchedule s = d.schedule();
    entity.applyActivity(
        d.kind().name(),
        d.endsAt(),
        s == null ? null : s.useId(),
        s == null ? null : s.partId(),
        s == null ? null : mask(s.weekdays()),
        s == null ? null : s.startMinute(),
        s == null ? null : s.endMinute(),
        s == null ? null : s.firstDate(),
        s == null ? null : s.lastDate(),
        event.endedFrom(),
        year(d.birthYears().from()),
        year(d.birthYears().to()),
        d.pricePeriod().name());
  }

  static Event toDomain(EventEntity e) {
    String descriptionGl = e.descriptionGl();
    String descriptionEs = e.descriptionEs();
    EventKind kind = EventKind.valueOf(e.kind());
    EventSchedule schedule = null;
    if (kind == EventKind.SCHEDULED) {
      schedule =
          new EventSchedule(
              e.useId(),
              e.partId(),
              days(Objects.requireNonNull(e.weekdays())),
              Objects.requireNonNull(e.startMinute()),
              Objects.requireNonNull(e.endMinute()),
              Objects.requireNonNull(e.firstDate()),
              Objects.requireNonNull(e.lastDate()));
    }
    Short from = e.birthYearFrom();
    Short to = e.birthYearTo();
    EventDetails details =
        new EventDetails(
            new EventText(e.nameGl(), e.nameEs()),
            descriptionGl == null || descriptionEs == null
                ? null
                : new EventText(descriptionGl, descriptionEs),
            kind,
            e.startsAt(),
            e.endsAt(),
            new EventPlace(e.facilityId(), e.place()),
            schedule,
            e.capacity(),
            e.priceCents(),
            PricePeriod.valueOf(e.pricePeriod()),
            e.registrationDeadline(),
            e.published(),
            e.approval(),
            new BirthYears(
                from == null ? null : from.intValue(), to == null ? null : to.intValue()));
    Long version = e.version();
    return Event.rehydrate(
        new EventId(e.getId()),
        details,
        e.cancelledAt(),
        e.endedFrom(),
        version == null ? 0 : version,
        e.createdAt(),
        e.updatedAt());
  }

  /** Luns = 1 … domingo = 64, coma os usos de facility. */
  static short mask(SortedSet<DayOfWeek> days) {
    int mask = 0;
    for (DayOfWeek d : days) {
      mask |= 1 << (d.getValue() - 1);
    }
    return (short) mask;
  }

  static SortedSet<DayOfWeek> days(short mask) {
    SortedSet<DayOfWeek> days = new TreeSet<>();
    for (DayOfWeek d : DayOfWeek.values()) {
      if ((mask & (1 << (d.getValue() - 1))) != 0) {
        days.add(d);
      }
    }
    return days;
  }

  private static @Nullable Short year(@Nullable Integer year) {
    return year == null ? null : year.shortValue();
  }
}
