// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Rango de datas dun uso «só unhas datas», cos dous extremos incluídos (P5: un día = rango dun
 * día). Que remate despois de empezar compróbao {@link FacilityUse}, co campo do erro.
 */
public record UsePeriod(LocalDate from, LocalDate to) {

  public static final Comparator<UsePeriod> ORDER =
      Comparator.comparing(UsePeriod::from).thenComparing(UsePeriod::to);

  public UsePeriod {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
  }

  public boolean overlaps(UsePeriod other) {
    return !from.isAfter(other.to) && !other.from.isAfter(to);
  }

  /**
   * Os días de {@code periods} que non están en {@code covered}, como rangos ordenados. Serve para
   * preguntar polas reservas afectadas só nos días que un uso que bloquea engade.
   */
  public static List<UsePeriod> uncovered(List<UsePeriod> periods, List<UsePeriod> covered) {
    List<UsePeriod> pending = new ArrayList<>(periods);
    pending.sort(ORDER);
    List<UsePeriod> result = new ArrayList<>();
    for (UsePeriod period : pending) {
      List<UsePeriod> pieces = List.of(period);
      for (UsePeriod hole : covered) {
        List<UsePeriod> next = new ArrayList<>();
        for (UsePeriod piece : pieces) {
          next.addAll(piece.minus(hole));
        }
        pieces = next;
      }
      result.addAll(pieces);
    }
    return List.copyOf(result);
  }

  private List<UsePeriod> minus(UsePeriod hole) {
    if (!overlaps(hole)) {
      return List.of(this);
    }
    List<UsePeriod> pieces = new ArrayList<>(2);
    if (from.isBefore(hole.from)) {
      pieces.add(new UsePeriod(from, hole.from.minusDays(1)));
    }
    if (to.isAfter(hole.to)) {
      pieces.add(new UsePeriod(hole.to.plusDays(1), to));
    }
    return pieces;
  }
}
