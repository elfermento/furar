// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que ocuparía unha actividade do concello nunha instalación (ADR-0041): as sesións libres e os
 * días que non se crean, coas mesmas clases ca unha tempada (ADR-0039, punto 8). Sen persoas: de
 * quen é cada reserva que choca, só o nome que xa ve o persoal.
 */
public record ActivityPlan(
    List<Session> sessions,
    List<LocalDate> holidays,
    List<LocalDate> useOff,
    List<LocalDate> closed,
    List<Conflict> conflicts) {

  public ActivityPlan {
    sessions = List.copyOf(sessions);
    holidays = List.copyOf(holidays);
    useOff = List.copyOf(useOff);
    closed = List.copyOf(closed);
    conflicts = List.copyOf(conflicts);
  }

  /** Unha sesión que se crea. */
  public record Session(LocalDate date, Instant start, Instant end) {

    public Session {
      Objects.requireNonNull(date, "date");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }

  /** Que hai un día que choca. */
  public enum ConflictKind {
    OTHER_USE_EXCLUSIVE,
    OUT_OF_HOURS,
    BLOCK,
    BOOKING,
    SESSION
  }

  /**
   * Un día que xa ten algo e non se crea.
   *
   * @param bookingId a reserva ou a sesión que o pisa, para cancelala desde a lista
   * @param name de quen é a reserva, ou de que entidade ou actividade a sesión
   * @param label o motivo do bloqueo ou o nome do uso exclusivo
   */
  public record Conflict(
      LocalDate date,
      Instant start,
      Instant end,
      ConflictKind kind,
      @Nullable UUID bookingId,
      @Nullable String name,
      @Nullable String label) {

    public Conflict {
      Objects.requireNonNull(date, "date");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      Objects.requireNonNull(kind, "kind");
    }
  }

  /** As datas con choque, en orde: as que tiña que ver o xestor. */
  public SortedSet<LocalDate> conflictDates() {
    TreeSet<LocalDate> dates = new TreeSet<>();
    conflicts.forEach(c -> dates.add(c.date()));
    return dates;
  }
}
