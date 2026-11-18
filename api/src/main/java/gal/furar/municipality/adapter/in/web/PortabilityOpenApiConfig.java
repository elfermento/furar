// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.shared.portability.MunicipalityDataSection;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * No OpenAPI, {@code sections} do ficheiro {@code furar-export} é un obxecto cunha propiedade por
 * sección co esquema da súa fila (ADR-0018): un módulo novo aparece no contrato só con publicar a
 * súa {@link MunicipalityDataSection}.
 */
@Configuration(proxyBeanMethods = false)
class PortabilityOpenApiConfig {

  static final String SECTIONS = "MunicipalityExportSections";
  private static final String REF = "#/components/schemas/";

  @Bean
  OpenApiCustomizer portabilitySections(ObjectProvider<MunicipalityDataSection<?>> sections) {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      Map<String, Schema> schemas = openApi.getComponents().getSchemas();
      ObjectSchema sectionsSchema = new ObjectSchema();
      sectionsSchema.setDescription("Unha propiedade por módulo; filas na orde de importación");
      for (MunicipalityDataSection<?> section : sections.orderedStream().toList()) {
        String row = section.rowType().getSimpleName();
        ModelConverters.getInstance().readAll(section.rowType()).forEach(schemas::putIfAbsent);
        sectionsSchema.addProperty(
            section.key(), new ArraySchema().items(new Schema<>().$ref(REF + row)));
      }
      schemas.put(SECTIONS, sectionsSchema);
      for (String owner : List.of("MunicipalityExportResponse", "MunicipalityImportRequest")) {
        Schema<?> schema = schemas.get(owner);
        if (schema != null && schema.getProperties() != null) {
          schema.getProperties().put("sections", new Schema<>().$ref(REF + SECTIONS));
        }
      }
    };
  }
}
