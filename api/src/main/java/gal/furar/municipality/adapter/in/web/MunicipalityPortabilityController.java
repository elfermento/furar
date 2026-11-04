// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.MunicipalityDataRequest;
import gal.furar.municipality.adapter.in.web.dto.MunicipalityDataResponse;
import gal.furar.municipality.adapter.in.web.dto.MunicipalityExportResponse;
import gal.furar.municipality.adapter.in.web.dto.MunicipalityImportRequest;
import gal.furar.municipality.adapter.in.web.dto.MunicipalityResponse;
import gal.furar.municipality.adapter.in.web.dto.ThemeRequest;
import gal.furar.municipality.application.port.in.ExportMunicipalityUseCase;
import gal.furar.municipality.application.port.in.ImportMunicipalityCommand;
import gal.furar.municipality.application.port.in.ImportMunicipalityUseCase;
import gal.furar.municipality.application.port.in.MunicipalityExport;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.in.ThemeData;
import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.portability.MunicipalityDataSection;
import gal.furar.shared.portability.PortabilityError;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Portabilidade (ADR-0018): o xestor descarga todos os datos do seu concello e a plataforma crea un
 * concello desde ese ficheiro noutra instalación.
 */
@RestController
@Tag(name = "municipalities", description = "Concellos")
class MunicipalityPortabilityController {

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final ExportMunicipalityUseCase export;
  private final ImportMunicipalityUseCase importer;
  private final Map<String, MunicipalityDataSection<?>> sections = new LinkedHashMap<>();
  private final JsonMapper json;

  MunicipalityPortabilityController(
      ExportMunicipalityUseCase export,
      ImportMunicipalityUseCase importer,
      List<MunicipalityDataSection<?>> sections,
      JsonMapper json) {
    this.export = export;
    this.importer = importer;
    sections.forEach(section -> this.sections.put(section.key(), section));
    this.json = json;
  }

  @GetMapping("/api/v1/municipalities/{slug}/export")
  @Operation(
      operationId = "exportMunicipality",
      summary = "Descargar todos os datos do concello (furar-export v3)")
  ResponseEntity<MunicipalityExportResponse> export(@PathVariable String slug) {
    MunicipalityExport result = export.export();
    Municipality m = result.municipality();
    Map<String, List<Object>> sections = new LinkedHashMap<>();
    result.sections().forEach((key, rows) -> sections.put(key, List.copyOf(rows)));
    var body =
        new MunicipalityExportResponse(
            MunicipalityExportResponse.FORMAT,
            MunicipalityExportResponse.VERSION,
            result.exportedAt(),
            new MunicipalityDataResponse(
                m.id().value(),
                m.slug().value(),
                m.name(),
                m.languages().stream().map(Language::code).toList(),
                m.defaultLanguage().code(),
                MunicipalityWebMapper.theme(m.theme()),
                MunicipalityWebMapper.legal(m.legal()),
                MunicipalityWebMapper.legalTexts(m.legalTexts()),
                result.customFields().toJson(),
                m.createdAt(),
                m.updatedAt()),
            sections);
    String filename =
        "furar-export-" + m.slug().value() + "-" + DAY.format(result.exportedAt()) + ".json";
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(body);
  }

  @PostMapping("/api/v1/municipality-imports")
  @Operation(
      operationId = "importMunicipality",
      summary = "Crear un concello desde un ficheiro furar-export v1, v2 ou v3 (plataforma)")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<MunicipalityResponse> importMunicipality(
      @Valid @RequestBody MunicipalityImportRequest request) {
    MunicipalityDataRequest m = request.municipality();
    ThemeRequest t = m.theme();
    MunicipalityView created =
        importer.importMunicipality(
            new ImportMunicipalityCommand(
                m.id(),
                m.slug(),
                m.name(),
                m.languages(),
                m.defaultLanguage(),
                new ThemeData(t.primary(), t.onPrimary(), t.accent(), t.background(), t.text()),
                m.customFields(),
                m.createdAt(),
                m.updatedAt(),
                typedSections(request.version(), request.sections()),
                MunicipalityWebMapper.legal(m.legal()),
                MunicipalityWebMapper.legalTexts(m.legalTexts())));
    Long version = created.municipality().version();
    return ResponseEntity.created(
            URI.create("/api/v1/municipalities/" + created.municipality().slug().value()))
        .eTag(ETags.of(version == null ? 0 : version))
        .body(MunicipalityWebMapper.toResponse(created));
  }

  /**
   * Cada fila co tipo da súa sección; as dun ficheiro antigo pasan antes pola súa sección para ter
   * a forma actual. As claves descoñecidas rexéitaas o caso de uso.
   */
  private Map<String, List<?>> typedSections(int version, Map<String, List<Object>> raw) {
    Map<String, List<?>> result = new LinkedHashMap<>();
    if (raw == null) {
      return result;
    }
    raw.forEach(
        (key, rows) -> {
          MunicipalityDataSection<?> section = sections.get(key);
          if (section == null) {
            result.put(key, rows == null ? List.of() : rows);
            return;
          }
          List<Object> typed = new ArrayList<>();
          for (Object row : rows == null ? List.of() : rows) {
            try {
              typed.add(json.convertValue(upgrade(section, version, row), section.rowType()));
            } catch (JacksonException | IllegalArgumentException e) {
              throw new DomainException(
                  PortabilityError.INVALID_FILE, Map.of("reason", "row", "section", key));
            }
          }
          result.put(key, typed);
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Object upgrade(MunicipalityDataSection<?> section, int version, Object row) {
    if (version >= MunicipalityExportResponse.VERSION || !(row instanceof Map<?, ?> fields)) {
      // Unha fila que non é un obxecto fallará ao lela co tipo da sección.
      return row;
    }
    return section.upgrade(version, (Map<String, Object>) fields);
  }
}
