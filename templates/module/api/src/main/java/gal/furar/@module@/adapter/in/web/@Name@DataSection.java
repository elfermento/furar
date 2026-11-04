// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.in.web;

import gal.furar.@module@.adapter.in.web.dto.@Name@Response;
import gal.furar.@module@.application.port.in.Export@Names@UseCase;
import gal.furar.@module@.application.port.in.Import@Names@UseCase;
import gal.furar.@module@.application.port.in.Restore@Name@Command;
import gal.furar.@module@.application.port.in.@Name@View;
import gal.furar.shared.portability.MunicipalityDataSection;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sección {@code @module@} do ficheiro furar-export (ADR-0018), co mesmo DTO ca a API. A orde é
 * a de importación: despois das seccións das que depende (customfields vai en 100).
 */
@Component
@Order(500)
class @Name@DataSection implements MunicipalityDataSection<@Name@Response> {

  private final Export@Names@UseCase export;
  private final Import@Names@UseCase importer;

  @Name@DataSection(Export@Names@UseCase export, Import@Names@UseCase importer) {
    this.export = export;
    this.importer = importer;
  }

  @Override
  public String key() {
    return "@module@";
  }

  @Override
  public Class<@Name@Response> rowType() {
    return @Name@Response.class;
  }

  @Override
  public List<@Name@Response> exportRows() {
    return export.exportAll().stream()
        .map(
            value ->
                @Name@WebMapper.toResponse(
                    new @Name@View(
                        value.customFields(), // @cf
                        value)))
        .toList();
  }

  @Override
  public void importRows(List<@Name@Response> rows) {
    importer.importAll(
        rows.stream()
            .map(
                row ->
                    new Restore@Name@Command(
                        row.id(),
                        row.name(),
                        row.customFields(), // @cf
                        row.createdAt(),
                        row.updatedAt()))
            .toList());
  }
}
