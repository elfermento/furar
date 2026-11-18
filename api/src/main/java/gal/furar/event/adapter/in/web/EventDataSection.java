// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web;

import gal.furar.event.adapter.in.web.dto.EventDataResponse;
import gal.furar.event.application.port.in.ExportEventsUseCase;
import gal.furar.event.application.port.in.ImportEventsUseCase;
import gal.furar.shared.portability.MunicipalityDataSection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sección {@code event} do ficheiro furar-export (ADR-0018): os eventos, sen as persoas apuntadas
 * (datos persoais, fóra do ficheiro coma as reservas) nin as sesións das actividades (son reservas;
 * ADR-0041). Despois das instalacións, ás que apunta o lugar.
 */
@Component
@Order(600)
class EventDataSection implements MunicipalityDataSection<EventDataResponse> {

  private final ExportEventsUseCase export;
  private final ImportEventsUseCase importer;

  EventDataSection(ExportEventsUseCase export, ImportEventsUseCase importer) {
    this.export = export;
    this.importer = importer;
  }

  @Override
  public String key() {
    return "event";
  }

  @Override
  public Class<EventDataResponse> rowType() {
    return EventDataResponse.class;
  }

  @Override
  public List<EventDataResponse> exportRows() {
    return export.exportAll().stream().map(EventWebMapper::row).toList();
  }

  /**
   * Ata a versión 4 todos os eventos eran dun día, sen límite de idade e cun prezo que se paga unha
   * vez: o fin é o inicio (ADR-0041).
   */
  @Override
  public Map<String, Object> upgrade(int version, Map<String, Object> row) {
    if (version >= 5) {
      return row;
    }
    Map<String, Object> upgraded = new LinkedHashMap<>(row);
    upgraded.putIfAbsent("kind", "ONE_DAY");
    upgraded.putIfAbsent("pricePeriod", "ONCE");
    upgraded.putIfAbsent("endsAt", row.get("startsAt"));
    return upgraded;
  }

  @Override
  public void importRows(List<EventDataResponse> rows) {
    importer.importAll(rows.stream().map(EventWebMapper::restore).toList());
  }
}
