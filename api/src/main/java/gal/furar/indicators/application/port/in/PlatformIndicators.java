// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpi;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.domain.Comparison;
import gal.furar.indicators.domain.ReportPeriod;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Os indicadores da plataforma: os totais de catro cifras e unha fila por concello. Sempre
 * agregado: nin nomes nin ids de persoas, nin reservas.
 *
 * @param totals a suma dos concellos, coa mesma comparación e evolución ca o panel de cada un
 * @param municipalities un por concello, por nome
 */
public record PlatformIndicators(
    ReportPeriod period,
    ReportPeriod previous,
    Comparison comparison,
    Totals totals,
    List<MunicipalityFigures> municipalities) {

  public PlatformIndicators {
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(totals, "totals");
    municipalities = List.copyOf(municipalities);
  }

  /**
   * As catro cifras sumadas. Sen persoas distintas: unha persoa que reserva en dous concellos
   * contaría dúas veces. A ocupación suma minutos reservados e abertos, así que é a media ponderada
   * polas horas abertas de cada concello.
   */
  public record Totals(
      Kpi<Long> bookings,
      Kpi<Occupancy> occupancy,
      Kpi<Double> reservedMinutes,
      Kpi<Long> paidCents) {}

  /**
   * As cifras dun concello no período, as mesmas do seu panel.
   *
   * @param people persoas distintas que reservaron no concello
   */
  public record MunicipalityFigures(
      UUID id,
      String slug,
      String name,
      long bookings,
      Occupancy occupancy,
      double reservedMinutes,
      long people,
      long paidCents) {}
}
