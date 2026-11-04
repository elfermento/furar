// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.List;

/**
 * O panel de indicadores (plan do dominio 3, §2.3): só números en bruto (contas, minutos e
 * céntimos); as porcentaxes e as frases calcúlaas a web. Sempre agregado: ningún nome nin dato da
 * veciñanza.
 *
 * @param previous o período anterior enteiro
 * @param comparison os tramos que compara a variación das cifras (no período en curso, ata hoxe)
 * @param occupancyByFacility as instalacións con horas abertas no período, por nome
 * @param dailyOccupancy a ocupación na unidade {@code period.breakdownUnit}
 * @param peakHours as 168 celas día × hora
 * @param cancellations na unidade {@code period.breakdownUnit}
 * @param revenue os cobros por instalación, por nome
 * @param people persoas distintas por mes: os seis que rematan no do período (doce no ano)
 */
public record IndicatorsResponse(
    IndicatorPeriodResponse period,
    IndicatorRangeResponse previous,
    IndicatorComparisonResponse comparison,
    IndicatorKpisResponse kpis,
    List<IndicatorFacilityOccupancyResponse> occupancyByFacility,
    List<IndicatorBucketOccupancyResponse> dailyOccupancy,
    List<IndicatorPeakHourResponse> peakHours,
    List<IndicatorCancellationsResponse> cancellations,
    List<IndicatorRevenueResponse> revenue,
    List<IndicatorPeopleResponse> people) {}
