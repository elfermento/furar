// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

import gal.furar.indicators.domain.Comparison;
import gal.furar.indicators.domain.ReportPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * O panel de indicadores (plan do dominio 3, §1.2.4–§1.2.6 e §2.3): só números en bruto (contas,
 * minutos e céntimos); as porcentaxes e as frases calcúlaas a web. Os minutos reservados xa levan o
 * peso do uso (partes e prazas) e, na ocupación, non pasan dos abertos.
 *
 * @param period o período pedido
 * @param previous o período co que se compara
 * @param comparison os tramos que compara a variación das cifras: «ata hoxe» no período en curso
 *     (ADR-0026, revisión do 2026-10-29)
 * @param occupancyByFacility as instalacións con horas abertas no período, por nome
 * @param dailyOccupancy a ocupación por día, semana ou mes ({@link ReportPeriod#breakdownUnit()})
 * @param peakHours as 168 celas día ISO × hora do período
 * @param cancellations as cancelacións na mesma desagregación ca {@code dailyOccupancy}
 * @param revenue os cobros por instalación, por nome
 * @param people persoas distintas por mes: os seis que rematan no do período (doce no ano)
 */
public record IndicatorsPanel(
    ReportPeriod period,
    ReportPeriod previous,
    Comparison comparison,
    Kpis kpis,
    List<FacilityOccupancy> occupancyByFacility,
    List<BucketOccupancy> dailyOccupancy,
    List<PeakHour> peakHours,
    List<Cancellations> cancellations,
    List<FacilityRevenue> revenue,
    List<People> people) {

  public IndicatorsPanel {
    occupancyByFacility = List.copyOf(occupancyByFacility);
    dailyOccupancy = List.copyOf(dailyOccupancy);
    peakHours = List.copyOf(peakHours);
    cancellations = List.copyOf(cancellations);
    revenue = List.copyOf(revenue);
    people = List.copyOf(people);
  }

  /**
   * As cinco cifras.
   *
   * @param reservedMinutes as horas reservadas, en minutos ponderados
   * @param paidCents o cobrado
   */
  public record Kpis(
      Kpi<Long> bookings,
      Kpi<Occupancy> occupancy,
      Kpi<Double> reservedMinutes,
      Kpi<Long> people,
      Kpi<Long> paidCents) {}

  /**
   * Unha cifra: o valor do período, o do anterior, os dous tramos da variación ({@link
   * IndicatorsPanel#comparison()}) e a evolución (12 semanas, 12 meses, 8 trimestres ou 5 anos; nun
   * rango libre, 12 meses), da máis antiga á máis nova.
   *
   * @param value o período enteiro, coas reservas xa feitas para os días que faltan (P8)
   * @param previous o período anterior enteiro
   * @param valueToDate o tramo actual da comparación: ata hoxe no en curso; se non, {@code value}
   * @param previousToDate o mesmo tramo do anterior; nun período pechado, {@code previous}
   */
  public record Kpi<T>(
      T value, T previous, T valueToDate, T previousToDate, List<Point<T>> evolution) {

    public Kpi {
      evolution = List.copyOf(evolution);
    }
  }

  /**
   * Un punto da evolución: o período enteiro da unidade.
   *
   * @param partial se é a unidade en curso (contén hoxe)
   */
  public record Point<T>(LocalDate from, LocalDate to, T value, boolean partial) {}

  /**
   * Unha ocupación: minutos reservados (ponderados e sen pasar dos abertos en cada instalación) e
   * minutos abertos.
   */
  public record Occupancy(double reservedMinutes, long openMinutes) {

    public static final Occupancy NONE = new Occupancy(0, 0);
  }

  public record FacilityOccupancy(UUID facilityId, String name, Occupancy occupancy) {}

  /**
   * Un tramo da desagregación, cortado polo período (a primeira semana pode empezar antes).
   *
   * @param partial se o tramo contén hoxe
   */
  public record BucketOccupancy(
      LocalDate from, LocalDate to, Occupancy occupancy, boolean partial) {}

  /**
   * @param isoWeekday de 1 (luns) a 7 (domingo)
   * @param hour de 0 a 23, en Europe/Madrid
   */
  public record PeakHour(int isoWeekday, int hour, Occupancy occupancy) {}

  /**
   * @param partial se o tramo contén hoxe
   */
  public record Cancellations(
      LocalDate from, LocalDate to, long inTime, long late, long byMunicipality, boolean partial) {}

  public record FacilityRevenue(
      UUID facilityId,
      String name,
      long paidCents,
      long pendingCents,
      long toRefundCents,
      long refundedCents) {}

  /**
   * @param partial se é o mes en curso
   */
  public record People(LocalDate from, LocalDate to, long people, boolean partial) {}
}
