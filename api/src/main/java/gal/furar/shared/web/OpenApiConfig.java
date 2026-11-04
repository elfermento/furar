// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * Contrato OpenAPI determinista (ADR-0009): servidor {@code /}, esquema {@code Problem} co {@code
 * code} como {@code enum} de todos os {@code ErrorCode}, seguridade por operación.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "Furar API",
            version = "v1",
            description = "Xestión e reserva de instalacións deportivas municipais",
            license = @License(name = "EUPL-1.2", identifier = "EUPL-1.2")),
    servers = @Server(url = "/"))
@SecurityScheme(
    name = OpenApiConfig.BEARER,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {

  public static final String BEARER = "bearer";
  public static final String PROBLEM = "Problem";
  private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM;

  /** Operacións sen {@code @PublicEndpoint} levan o requisito {@code bearer}. */
  @Bean
  OperationCustomizer securityPerOperation() {
    return (operation, handler) -> {
      if (!handler.hasMethodAnnotation(PublicEndpoint.class)) {
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
      }
      return operation;
    };
  }

  /**
   * Cabeceiras das respostas correctas (ADR-0009): todo 201 leva {@code Location} co URL do recurso
   * creado e, nos métodos {@link VersionedResource}, os 200 e 201 levan {@code ETag} coa versión
   * que hai que mandar en {@code If-Match}. Así o cliente xerado e quen lea o contrato as ven.
   */
  @Bean
  OperationCustomizer responseHeaders() {
    return (operation, handler) -> {
      if (operation.getResponses() == null) {
        return operation;
      }
      boolean versioned = handler.hasMethodAnnotation(VersionedResource.class);
      operation
          .getResponses()
          .forEach(
              (code, response) -> {
                if ("201".equals(code)) {
                  response.addHeaderObject(
                      "Location",
                      new Header()
                          .description("URL do recurso creado")
                          .required(true)
                          .schema(new StringSchema().format("uri-reference")));
                }
                if (versioned && ("200".equals(code) || "201".equals(code))) {
                  response.addHeaderObject(
                      "ETag",
                      new Header()
                          .description("Versión do recurso; mándaa en If-Match ao cambialo")
                          .required(true)
                          .schema(new StringSchema().example("\"3\"")));
                }
              });
      return operation;
    };
  }

  /**
   * Nas respostas, todo campo dun {@code record} que non leve {@code @Nullable} é obrigatorio: o
   * cliente TS xerado non ten que comprobar {@code undefined} onde a API sempre responde.
   */
  @Bean
  OpenApiCustomizer requiredResponseFields() {
    Map<String, List<String>> required = responseRequiredFields();
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      openApi
          .getComponents()
          .getSchemas()
          .forEach(
              (name, schema) -> {
                List<String> fields =
                    name.startsWith("PageResponse")
                        ? List.of("items", "page", "size", "totalItems")
                        : required.get(name);
                if (fields != null && schema.getProperties() != null) {
                  schema.setRequired(
                      fields.stream()
                          .filter(schema.getProperties()::containsKey)
                          .sorted()
                          .toList());
                }
              });
    };
  }

  /** {@code records} {@code *Response} dos paquetes {@code ..adapter.in.web.dto}. */
  static Map<String, List<String>> responseRequiredFields() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(
        (reader, factory) -> reader.getClassMetadata().getClassName().endsWith("Response"));
    Map<String, List<String>> result = new TreeMap<>();
    for (var candidate : scanner.findCandidateComponents(ErrorCatalog.BASE_PACKAGE)) {
      Class<?> type =
          ClassUtils.resolveClassName(
              candidate.getBeanClassName(), OpenApiConfig.class.getClassLoader());
      if (!type.isRecord() || !type.getPackageName().contains(".adapter.in.web.dto")) {
        continue;
      }
      List<String> fields = new ArrayList<>();
      for (RecordComponent component : type.getRecordComponents()) {
        if (!component.isAnnotationPresent(Nullable.class)
            && !component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
          fields.add(component.getName());
        }
      }
      result.put(type.getSimpleName(), fields);
    }
    return result;
  }

  /** Esquema {@code Problem} (RFC 9457) e resposta de erro por defecto en cada operación. */
  @Bean
  OpenApiCustomizer problemSchema(ErrorCatalog catalog) {
    return openApi -> {
      StringSchema code = new StringSchema();
      catalog.codes().forEach(code::addEnumItem);
      Schema<?> fieldError =
          new ObjectSchema()
              .addProperty("field", new StringSchema())
              .addProperty("code", code)
              .addProperty("params", new ObjectSchema().additionalProperties(true))
              .required(List.of("field", "code", "params"));
      Schema<?> problem =
          new ObjectSchema()
              .addProperty("type", new StringSchema().format("uri"))
              .addProperty("title", new StringSchema())
              .addProperty("status", new IntegerSchema().format("int32"))
              .addProperty("detail", new StringSchema())
              .addProperty("instance", new StringSchema().format("uri"))
              .addProperty("code", code)
              .addProperty("params", new ObjectSchema().additionalProperties(true))
              .addProperty("errors", new ArraySchema().items(new Schema<>().$ref("FieldError")))
              .addProperty("traceId", new StringSchema())
              .required(List.of("type", "title", "status", "code", "params", "traceId"));
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      openApi.getComponents().addSchemas("FieldError", fieldError);
      openApi.getComponents().addSchemas(PROBLEM, problem);
      ApiResponse error =
          new ApiResponse()
              .description("Erro RFC 9457")
              .content(
                  new Content()
                      .addMediaType(
                          "application/problem+json",
                          new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
      if (openApi.getPaths() != null) {
        openApi
            .getPaths()
            .values()
            .forEach(
                path ->
                    path.readOperations()
                        .forEach(
                            operation ->
                                operation.getResponses().addApiResponse("default", error)));
      }
    };
  }
}
