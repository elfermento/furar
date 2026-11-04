// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.SportResponse;
import gal.furar.facility.adapter.in.web.portability.dto.FacilityExportResponse;
import gal.furar.facility.application.port.in.ExportFacilitiesUseCase;
import gal.furar.facility.application.port.in.ImportFacilitiesUseCase;
import gal.furar.facility.application.port.in.RestoreCustomSportCommand;
import gal.furar.facility.application.port.in.RestoreFacilityCommand;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.portability.MunicipalityDataSection;
import gal.furar.shared.portability.PortabilityError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sección {@code facility} do ficheiro furar-export (ADR-0018): unha fila por instalación cos
 * campos da ficha e todo o que colga dela ({@link FacilityExportResponse}). Vai despois de {@code
 * customfields} (100), porque os campos propios se validan contra as definicións, e de {@code
 * facility-sports} ({@link CustomSportsDataSection}). Os deportes do concello viaxan tamén co seu
 * nome dentro das filas que os usan: un ficheiro sen a súa sección créaos desde aquí.
 */
@Component
@Order(500)
class FacilityDataSection implements MunicipalityDataSection<FacilityExportResponse> {

  static final String KEY = "facility";

  private final ExportFacilitiesUseCase export;
  private final ImportFacilitiesUseCase importer;

  FacilityDataSection(ExportFacilitiesUseCase export, ImportFacilitiesUseCase importer) {
    this.export = export;
    this.importer = importer;
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public Class<FacilityExportResponse> rowType() {
    return FacilityExportResponse.class;
  }

  @Override
  public List<FacilityExportResponse> exportRows() {
    return export.exportAll().stream().map(FacilityExportMapper::toRow).toList();
  }

  @Override
  public void importRows(List<FacilityExportResponse> rows) {
    Map<UUID, RestoreCustomSportCommand> customSports = new LinkedHashMap<>();
    List<RestoreFacilityCommand> commands = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      FacilityExportResponse row = rows.get(i);
      try {
        for (SportResponse sport : FacilityExportMapper.sports(row)) {
          if (sport.customSportId() != null) {
            customSports.putIfAbsent(
                sport.customSportId(),
                new RestoreCustomSportCommand(
                    sport.customSportId(), sport.label().gl(), sport.label().es()));
          }
        }
        commands.add(FacilityExportMapper.toCommand(row));
      } catch (NullPointerException | IllegalArgumentException e) {
        // Falta un campo obrigatorio ou un enum non existe: non é un furar-export válido.
        throw new DomainException(
            PortabilityError.INVALID_FILE, Map.of("section", KEY, "row", i, "reason", "row"));
      }
    }
    importer.importAll(List.copyOf(customSports.values()), commands);
  }
}
