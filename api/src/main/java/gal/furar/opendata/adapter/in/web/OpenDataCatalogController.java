// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import gal.furar.opendata.adapter.in.web.dto.OpenDataSummaryResponse;
import gal.furar.opendata.application.port.in.Scope;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos abertos de todos os concellos publicados (sen concello no URL) (ADR-0034): o resumo da
 * páxina, os tres conxuntos e a ficha de catálogo. Sen sesión; os ficheiros e a ficha levan {@code
 * Access-Control-Allow-Origin: *}.
 */
@RestController
@RequestMapping("/api/v1/catalog/open-data")
@Tag(name = "open-data", description = "Datos abertos")
class OpenDataCatalogController {

  private final OpenDataFiles files;

  OpenDataCatalogController(OpenDataFiles files) {
    this.files = files;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "getOpenDataCatalog",
      summary = "Resumo da páxina «Datos abertos»: conxuntos, datas e se hai ficha")
  OpenDataSummaryResponse summary() {
    return files.summary(Scope.ALL);
  }

  @GetMapping("/facilities.{format}")
  @PublicEndpoint
  @Operation(
      operationId = "downloadOpenDataFacilitiesCatalog",
      summary = "Instalacións cos seus usos, en CSV (unha fila por uso), JSON ou GeoJSON")
  @ApiResponse(
      responseCode = "200",
      description = "Ficheiro",
      content = @Content(schema = @Schema(type = "string", format = "binary")))
  void facilities(
      @PathVariable String format,
      @Parameter(description = "gl (por defecto) ou es") @RequestParam(required = false)
          @Nullable String lang,
      HttpServletRequest request,
      HttpServletResponse response) {
    files.facilities(Scope.ALL, "todos", format, lang, request, response);
  }

  @GetMapping("/closures.{format}")
  @PublicEndpoint
  @Operation(
      operationId = "downloadOpenDataClosuresCatalog",
      summary = "Festivos e días enteiros pechados, en CSV ou JSON")
  @ApiResponse(
      responseCode = "200",
      description = "Ficheiro",
      content = @Content(schema = @Schema(type = "string", format = "binary")))
  void closures(
      @PathVariable String format,
      @Parameter(description = "gl (por defecto) ou es") @RequestParam(required = false)
          @Nullable String lang,
      HttpServletRequest request,
      HttpServletResponse response) {
    files.closures(Scope.ALL, "todos", format, lang, request, response);
  }

  @GetMapping("/occupancy.{format}")
  @PublicEndpoint
  @Operation(
      operationId = "downloadOpenDataOccupancyCatalog",
      summary =
          "Ocupación por cuartos de hora entre desde e ata (AAAA-MM-DD, incluídas; sen elas, o"
              + " último mes enteiro), dentro dos últimos 12 meses")
  @ApiResponse(
      responseCode = "200",
      description = "Ficheiro",
      content = @Content(schema = @Schema(type = "string", format = "binary")))
  void occupancy(
      @PathVariable String format,
      @Parameter(description = "Primeiro día, AAAA-MM-DD") @RequestParam(required = false)
          @Nullable String desde,
      @Parameter(description = "Último día, AAAA-MM-DD") @RequestParam(required = false)
          @Nullable String ata,
      @Parameter(description = "gl (por defecto) ou es") @RequestParam(required = false)
          @Nullable String lang,
      HttpServletRequest request,
      HttpServletResponse response) {
    files.occupancy(Scope.ALL, "todos", format, desde, ata, lang, request, response);
  }

  @GetMapping("/dcat.rdf")
  @PublicEndpoint
  @Operation(
      operationId = "downloadOpenDataCatalogCatalog",
      summary = "Ficha de catálogo DCAT-AP-ES en RDF/XML; 404 sen DIR3")
  @ApiResponse(
      responseCode = "200",
      description = "RDF/XML",
      content = @Content(schema = @Schema(type = "string", format = "binary")))
  void catalog(HttpServletRequest request, HttpServletResponse response) {
    files.catalog(Scope.ALL, "todos", request, response);
  }
}
