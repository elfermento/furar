// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpi;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpis;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Point;
import gal.furar.indicators.application.port.in.PlatformIndicators.Totals;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * Os totais da plataforma: as cifras dos paneis de cada concello, sumadas valor a valor e punto a
 * punto da evolución (todos teñen os mesmos tramos, porque saen do mesmo período). A ocupación suma
 * minutos reservados e abertos: a media ponderada polas horas abertas, non a media das porcentaxes.
 * Puro.
 */
final class PlatformTotals {

  private PlatformTotals() {}

  static Totals sum(List<Kpis> panels, Totals empty) {
    if (panels.isEmpty()) {
      return empty;
    }
    return new Totals(
        sum(panels, Kpis::bookings, Long::sum),
        sum(panels, Kpis::occupancy, PlatformTotals::plus),
        sum(panels, Kpis::reservedMinutes, Double::sum),
        sum(panels, Kpis::paidCents, Long::sum));
  }

  static Occupancy plus(Occupancy a, Occupancy b) {
    return new Occupancy(
        a.reservedMinutes() + b.reservedMinutes(), a.openMinutes() + b.openMinutes());
  }

  private static <T> Kpi<T> sum(
      List<Kpis> panels, Function<Kpis, Kpi<T>> pick, BinaryOperator<T> add) {
    Kpi<T> total = pick.apply(panels.getFirst());
    for (Kpis panel : panels.subList(1, panels.size())) {
      Kpi<T> next = pick.apply(panel);
      total =
          new Kpi<>(
              add.apply(total.value(), next.value()),
              add.apply(total.previous(), next.previous()),
              add.apply(total.valueToDate(), next.valueToDate()),
              add.apply(total.previousToDate(), next.previousToDate()),
              points(total.evolution(), next.evolution(), add));
    }
    return total;
  }

  private static <T> List<Point<T>> points(
      List<Point<T>> a, List<Point<T>> b, BinaryOperator<T> add) {
    if (a.size() != b.size()) {
      throw new IllegalStateException("evolutions differ in size");
    }
    List<Point<T>> result = new ArrayList<>(a.size());
    for (int i = 0; i < a.size(); i++) {
      Point<T> p = a.get(i);
      Point<T> q = b.get(i);
      if (!p.from().equals(q.from())) {
        throw new IllegalStateException("evolutions differ in buckets");
      }
      result.add(new Point<>(p.from(), p.to(), add.apply(p.value(), q.value()), p.partial()));
    }
    return result;
  }
}
