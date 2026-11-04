// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web;

import gal.furar.customfields.adapter.in.web.dto.CustomFieldDefinitionResponse;
import gal.furar.customfields.application.port.in.ExportCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.ImportCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.RestoreCustomFieldDefinitionCommand;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.portability.MunicipalityDataSection;
import gal.furar.shared.portability.PortabilityError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sección {@code customfields} do ficheiro {@code furar-export}: as definicións, co mesmo DTO ca a
 * API (ADR-0018). Vai primeiro: os valores das outras seccións valídanse contra elas. Le tamén as
 * filas das versións 1 (un destino por definición) e 2 (arquivado no canto de desactivado).
 */
@Component
@Order(100)
class CustomFieldsDataSection implements MunicipalityDataSection<CustomFieldDefinitionResponse> {

  static final String KEY = "customfields";

  /**
   * Campos da fila v1 que a v2 xa non ten: o destino, obrigatorio e visibilidade pasan a {@code
   * usages[]}; orde, grupo e condicións desaparecen do modelo (especificación, bloque 1b; P28).
   */
  private static final Set<String> V1_ONLY =
      Set.of("target", "required", "visibility", "position", "group", "appliesWhen");

  private final ExportCustomFieldDefinitionsUseCase export;
  private final ImportCustomFieldDefinitionsUseCase importer;

  CustomFieldsDataSection(
      ExportCustomFieldDefinitionsUseCase export, ImportCustomFieldDefinitionsUseCase importer) {
    this.export = export;
    this.importer = importer;
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public Class<CustomFieldDefinitionResponse> rowType() {
    return CustomFieldDefinitionResponse.class;
  }

  @Override
  public List<CustomFieldDefinitionResponse> exportRows() {
    return export.exportAll().stream()
        .map(d -> CustomFieldWebMapper.toResponse(d, Language.GL))
        .toList();
  }

  @Override
  public void importRows(List<CustomFieldDefinitionResponse> rows) {
    List<RestoreCustomFieldDefinitionCommand> commands = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      try {
        commands.add(CustomFieldWebMapper.toRestoreCommand(rows.get(i)));
      } catch (NullPointerException | IllegalArgumentException e) {
        // Falta un campo obrigatorio ou un enum non existe: o ficheiro non é un furar-export
        // válido.
        throw new DomainException(
            PortabilityError.INVALID_FILE, Map.of("section", KEY, "row", i, "reason", "row"));
      }
    }
    importer.importAll(commands);
  }

  /**
   * Unha fila v1 é unha definición cun só destino: convértese nun único uso co seu {@code target},
   * {@code required} e {@code visibility}, e ignóranse {@code position}, {@code group} e {@code
   * appliesWhen}. Unha fila sen {@code target} rexéitase ao lela, como calquera fila incompleta.
   * Ata a v2, o estado {@code ARCHIVED} e a marca {@code archived} das opcións pasan a {@code
   * INACTIVE} e {@code inactive}. Unha fila v2 con varios usos non se parte (os valores das outras
   * seccións van polo código): o caso de uso rexéitaa con {@code customfield.single-usage}.
   */
  @Override
  public Map<String, Object> upgrade(int version, Map<String, Object> row) {
    Map<String, Object> upgraded = new LinkedHashMap<>(row);
    if (version == 1) {
      Map<String, Object> usage = new LinkedHashMap<>();
      usage.put("target", row.get("target"));
      usage.put("required", row.getOrDefault("required", false));
      usage.put("visibility", row.get("visibility"));
      upgraded.keySet().removeAll(V1_ONLY);
      upgraded.put("usages", List.of(usage));
    }
    if (version <= 2) {
      if ("ARCHIVED".equals(upgraded.get("status"))) {
        upgraded.put("status", "INACTIVE");
      }
      if (upgraded.get("options") instanceof List<?> options) {
        upgraded.put("options", options.stream().map(CustomFieldsDataSection::option).toList());
      }
    }
    return upgraded;
  }

  private static Object option(Object option) {
    if (!(option instanceof Map<?, ?> fields)) {
      return option;
    }
    Map<Object, Object> upgraded = new LinkedHashMap<>(fields);
    Object archived = upgraded.remove("archived");
    upgraded.put("inactive", archived == null ? Boolean.FALSE : archived);
    return upgraded;
  }
}
