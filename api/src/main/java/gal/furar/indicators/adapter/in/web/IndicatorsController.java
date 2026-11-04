// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web;

import gal.furar.indicators.adapter.in.web.dto.IndicatorFiltersResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorPeriodKindValue;
import gal.furar.indicators.adapter.in.web.dto.IndicatorsResponse;
import gal.furar.indicators.application.port.in.IndicatorsQuery;
import gal.furar.indicators.application.port.in.ReadIndicatorFiltersUseCase;
import gal.furar.indicators.application.port.in.ReadIndicatorsUseCase;
import gal.furar.indicators.domain.PeriodKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O panel de indicadores do concello e os seus filtros (ADR-0026; plan do dominio 3, §2.3). Só
 * xestión ({@code indicators.view}); sempre agregado.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/indicators")
@Tag(name = "indicators", description = "Indicadores")
class IndicatorsController {

  private final ReadIndicatorsUseCase indicators;
  private final ReadIndicatorFiltersUseCase filters;

  IndicatorsController(ReadIndicatorsUseCase indicators, ReadIndicatorFiltersUseCase filters) {
    this.indicators = indicators;
    this.filters = filters;
  }

  @GetMapping
  @Operation(
      operationId = "getIndicators",
      summary =
          "O panel enteiro nunha petición: cinco cifras co anterior e a evolución, ocupación por"
              + " instalación e día a día, horas punta, cancelacións, cobros e persoas por mes."
              + " Números en bruto (contas, minutos e céntimos), días de Europe/Madrid."
              + " 400 indicators.period-invalid ou indicators.filter-invalid")
  IndicatorsResponse read(
      @PathVariable String slug,
      @Parameter(description = "Período (sen el, MONTH)") @RequestParam(defaultValue = "MONTH")
          IndicatorPeriodKindValue period,
      @Parameter(
              description = "Data que ancora a semana, o mes, o trimestre ou o ano (sen ela, hoxe)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate date,
      @Parameter(description = "Primeiro día do rango libre (CUSTOM)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @Parameter(description = "Último día do rango libre (CUSTOM), incluído")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      @Parameter(description = "Unha instalación do concello (sen ela: todas)")
          @RequestParam(required = false)
          @Nullable UUID facilityId,
      @Parameter(description = "Usos do concello, sen repetir e ata 50 (sen eles: todos)")
          @RequestParam(name = "useId", required = false)
          @Nullable List<UUID> useIds) {
    return IndicatorsWebMapper.panel(
        indicators.read(
            new IndicatorsQuery(
                PeriodKind.valueOf(period.name()),
                date,
                from,
                to,
                facilityId,
                useIds == null ? List.of() : useIds)));
  }

  @GetMapping("/filters")
  @Operation(
      operationId = "getIndicatorFilters",
      summary =
          "As instalacións (as de baixa, só con reservas) e os usos (os arquivados, só con"
              + " reservas) que se poden escoller no panel")
  IndicatorFiltersResponse filters(@PathVariable String slug) {
    return IndicatorsWebMapper.filters(filters.filters());
  }
}
