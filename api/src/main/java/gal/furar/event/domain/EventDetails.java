// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * O que escribe o concello nun evento: nome e descrición en gl e es, o tipo (dun día ou con
 * horario, ADR-0041), cando, onde, o horario dunha actividade, prazas (nulo: sen límite), prezo en
 * céntimos (nulo: gratis) e cada canto se paga, «Apuntarse ata» (nulo: ata que empeza ou, nunha
 * actividade, ata que remata), se está publicado, se apuntarse pide aprobación («Con aprobación») e
 * o límite de idade polo ano de nacemento.
 *
 * <p>Nun evento dun día, {@code endsAt} é {@code startsAt}. Nunha actividade, {@code startsAt} é o
 * inicio da primeira sesión e {@code endsAt} o fin da última: sáeno das sesións, non do formulario.
 */
public record EventDetails(
    EventText name,
    @Nullable EventText description,
    EventKind kind,
    Instant startsAt,
    Instant endsAt,
    EventPlace place,
    @Nullable EventSchedule schedule,
    @Nullable Integer capacity,
    @Nullable Integer priceCents,
    PricePeriod pricePeriod,
    @Nullable Instant registrationDeadline,
    boolean published,
    boolean approval,
    BirthYears birthYears) {

  public static final int CAPACITY_MAX = 10_000;

  /** 1.000,00 €. */
  public static final int PRICE_MAX_CENTS = 100_000;

  public EventDetails {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(startsAt, "startsAt");
    Objects.requireNonNull(place, "place");
    pricePeriod = pricePeriod == null ? PricePeriod.ONCE : pricePeriod;
    birthYears = birthYears == null ? BirthYears.NONE : birthYears;
    if (kind == EventKind.ONE_DAY) {
      endsAt = startsAt;
    }
    Objects.requireNonNull(endsAt, "endsAt");
    if ((kind == EventKind.SCHEDULED) != (schedule != null)
        || endsAt.isBefore(startsAt)
        || (schedule != null && (place.facilityId() != null) != (schedule.useId() != null))) {
      throw new DomainException(EventError.SCHEDULE_INVALID);
    }
    if (capacity != null && (capacity < 1 || capacity > CAPACITY_MAX)) {
      throw new DomainException(EventError.CAPACITY_INVALID, Map.of("max", CAPACITY_MAX));
    }
    if (priceCents != null && (priceCents < 1 || priceCents > PRICE_MAX_CENTS)) {
      throw new DomainException(EventError.PRICE_INVALID, Map.of("max", PRICE_MAX_CENTS / 100));
    }
    if (registrationDeadline != null
        && registrationDeadline.isAfter(lastStart(kind, startsAt, endsAt))) {
      throw new DomainException(EventError.DEADLINE_INVALID);
    }
  }

  /** Un evento dun día (ADR-0038), sen límite de idade e cun prezo que se paga unha vez. */
  public EventDetails(
      EventText name,
      @Nullable EventText description,
      Instant startsAt,
      EventPlace place,
      @Nullable Integer capacity,
      @Nullable Integer priceCents,
      @Nullable Instant registrationDeadline,
      boolean published,
      boolean approval) {
    this(
        name,
        description,
        EventKind.ONE_DAY,
        startsAt,
        startsAt,
        place,
        null,
        capacity,
        priceCents,
        PricePeriod.ONCE,
        registrationDeadline,
        published,
        approval,
        BirthYears.NONE);
  }

  /**
   * Cando deixa de admitir inscricións: «Apuntarse ata» ou, sen ela, cando empeza (un evento dun
   * día) ou cando remata (unha actividade: apúntase tamén xa empezada, se hai praza).
   */
  public Instant closesAt() {
    return registrationDeadline == null ? lastStart(kind, startsAt, endsAt) : registrationDeadline;
  }

  /** Se cambiou o que se avisa por correo ás persoas apuntadas: data, hora ou lugar. */
  public boolean movedFrom(EventDetails before) {
    return !startsAt.equals(before.startsAt) || !place.equals(before.place);
  }

  /** Se é unha actividade con horario. */
  public boolean scheduled() {
    return kind == EventKind.SCHEDULED;
  }

  /** As mesmas, con outro fin (ao rematar antes unha actividade). */
  public EventDetails withEndsAt(Instant end) {
    return new EventDetails(
        name,
        description,
        kind,
        startsAt,
        end,
        place,
        schedule,
        capacity,
        priceCents,
        pricePeriod,
        registrationDeadline,
        published,
        approval,
        birthYears);
  }

  /** As mesmas, coas datas e o horario doutras (nunha actividade non se cambian ao editar). */
  public EventDetails withTimesOf(EventDetails other) {
    return new EventDetails(
        name,
        description,
        kind,
        other.startsAt,
        other.endsAt,
        place,
        schedule,
        capacity,
        priceCents,
        pricePeriod,
        registrationDeadline,
        published,
        approval,
        birthYears);
  }

  private static Instant lastStart(EventKind kind, Instant startsAt, Instant endsAt) {
    return kind == EventKind.SCHEDULED ? endsAt : startsAt;
  }
}
