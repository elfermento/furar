// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.SportLabelResponse;
import gal.furar.facility.adapter.in.web.portability.dto.CustomSportExportResponse;
import gal.furar.facility.application.port.in.ExportFacilitiesUseCase;
import gal.furar.facility.application.port.in.ImportFacilitiesUseCase;
import gal.furar.facility.application.port.in.RestoreCustomSportCommand;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.portability.MunicipalityDataSection;
import gal.furar.shared.portability.PortabilityError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sección {@code facility-sports} do ficheiro furar-export (ADR-0018): todos os deportes creados
 * polo concello, tamén os que xa non usa ningunha instalación (dentro das filas de {@code facility}
 * só viaxan os usados). Vai antes ca {@link FacilityDataSection}.
 */
@Component
@Order(450)
class CustomSportsDataSection implements MunicipalityDataSection<CustomSportExportResponse> {

  static final String KEY = "facility-sports";

  private final ExportFacilitiesUseCase export;
  private final ImportFacilitiesUseCase importer;

  CustomSportsDataSection(ExportFacilitiesUseCase export, ImportFacilitiesUseCase importer) {
    this.export = export;
    this.importer = importer;
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public Class<CustomSportExportResponse> rowType() {
    return CustomSportExportResponse.class;
  }

  @Override
  public List<CustomSportExportResponse> exportRows() {
    return export.exportCustomSports().stream()
        .map(
            s ->
                new CustomSportExportResponse(
                    s.id().value(),
                    new SportLabelResponse(s.label().gl(), s.label().es()),
                    s.createdAt()))
        .toList();
  }

  @Override
  public void importRows(List<CustomSportExportResponse> rows) {
    List<RestoreCustomSportCommand> commands = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      CustomSportExportResponse row = rows.get(i);
      if (row.id() == null || row.label() == null) {
        throw new DomainException(
            PortabilityError.INVALID_FILE, Map.of("section", KEY, "row", i, "reason", "row"));
      }
      commands.add(
          new RestoreCustomSportCommand(
              row.id(), row.label().gl(), row.label().es(), row.createdAt()));
    }
    importer.importCustomSports(commands);
  }
}
