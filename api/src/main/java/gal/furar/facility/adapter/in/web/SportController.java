// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.CreateSportRequest;
import gal.furar.facility.adapter.in.web.dto.SportResponse;
import gal.furar.facility.application.port.in.SportsUseCase;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deportes para a tarxeta Datos: o catálogo de Furar e os do concello, e «+ crear» (especificación,
 * bloque 1). Lectura de xestión; escribe o xestor (o permiso vive no caso de uso).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/sports")
@Tag(name = "facilities", description = "Instalacións")
class SportController {

  private final SportsUseCase sports;

  SportController(SportsUseCase sports) {
    this.sports = sports;
  }

  @GetMapping
  @Operation(
      operationId = "listSports",
      summary = "Catálogo e deportes do concello, en orde alfabética segundo Accept-Language")
  List<SportResponse> list(@PathVariable String slug, HttpServletRequest request) {
    return sports.list(Locales.language(request)).stream().map(FacilityWebMapper::sport).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "createSport", summary = "Deporte novo do concello («+ crear»)")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  SportResponse create(@PathVariable String slug, @Valid @RequestBody CreateSportRequest request) {
    return FacilityWebMapper.sport(sports.create(FacilityWebMapper.toCommand(request)));
  }
}
