// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule;

import gal.furar.facility.adapter.in.web.schedule.dto.PublicHolidayResponse;
import gal.furar.facility.application.port.in.schedule.PublicHolidaysUseCase;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Festivos de Galicia (plan §2.2): datos públicos da plataforma, sen concello nin sesión. Un ano
 * sen publicar no BOE e no DOG devolve a lista baleira.
 */
@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "facilities", description = "Instalacións")
class PublicHolidayController {

  private final PublicHolidaysUseCase holidays;

  PublicHolidayController(PublicHolidaysUseCase holidays) {
    this.holidays = holidays;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listPublicHolidays",
      summary = "Festivos nacionais e autonómicos que se gozan en Galicia nun ano, con fonte")
  List<PublicHolidayResponse> list(@RequestParam int year) {
    return ScheduleWebMapper.holidays(holidays.holidays(year));
  }
}
