// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web;

import gal.furar.indicators.adapter.in.web.dto.IndicatorPeriodKindValue;
import gal.furar.indicators.adapter.in.web.dto.PlatformIndicatorsResponse;
import gal.furar.indicators.application.port.in.ReadPlatformIndicatorsUseCase;
import gal.furar.indicators.domain.PeriodKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Os indicadores da plataforma (ADR-0026, revisión do 2026-11-11): os totais e unha fila por
 * concello, só cifras. Sen concello na ruta; só a administración da plataforma ({@code
 * indicators.platform-view}).
 */
@RestController
@Tag(name = "indicators", description = "Indicadores")
class PlatformIndicatorsController {

  private final ReadPlatformIndicatorsUseCase indicators;

  PlatformIndicatorsController(ReadPlatformIndicatorsUseCase indicators) {
    this.indicators = indicators;
  }

  @GetMapping("/api/v1/indicators")
  @Operation(
      operationId = "getPlatformIndicators",
      summary =
          "Os indicadores de todos os concellos nun período: reservas, ocupación media, horas"
              + " reservadas e cobrado sumados, co anterior e a evolución, e unha fila por concello"
              + " (tamén as persoas distintas de cada un). Números en bruto, días de"
              + " Europe/Madrid. 400 indicators.period-invalid; 409"
              + " indicators.too-many-municipalities")
  PlatformIndicatorsResponse read(
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
          @Nullable LocalDate to) {
    return IndicatorsWebMapper.platform(
        indicators.read(PeriodKind.valueOf(period.name()), date, from, to));
  }
}
