// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * O que crearía unha tempada (ADR-0039, punto 8): as sesións libres e os días que non se crean,
 * cada un pola súa clase.
 */
public record SeriesPlan(
    List<Session> sessions,
    List<LocalDate> holidays,
    List<LocalDate> useOff,
    List<LocalDate> closed,
    List<SeriesConflict> conflicts) {

  public SeriesPlan {
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

  /** As datas con choque, en orde: as que tiña que ver o xestor ({@code skip}). */
  public SortedSet<LocalDate> conflictDates() {
    TreeSet<LocalDate> dates = new TreeSet<>();
    conflicts.forEach(c -> dates.add(c.date()));
    return dates;
  }

  /** Todas as datas que non se crean (festivos, uso non vixente, pechados e choques). */
  public SortedSet<LocalDate> skipped() {
    TreeSet<LocalDate> dates = new TreeSet<>(conflictDates());
    dates.addAll(holidays);
    dates.addAll(useOff);
    dates.addAll(closed);
    return dates;
  }
}
