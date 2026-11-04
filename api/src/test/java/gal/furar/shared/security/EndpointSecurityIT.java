// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * D15 (ADR-0007): percorre todos os controladores rexistrados e chama a cada operación sen token.
 * Toda operación sen {@link PublicEndpoint} responde 401 {@code auth.unauthenticated}; as públicas
 * non. A lista de operacións coincide coa de {@code openapi.json}: non hai endpoints sen contrato.
 */
class EndpointSecurityIT extends AbstractIT {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mapping;

  record Operation(String method, String path, boolean isPublic) {}

  @Test
  void everyNonPublicOperationRequiresAToken() throws IOException {
    String slug = uniqueSlug("sec");
    createMunicipality(slug);
    List<Operation> operations = operations();
    assertThat(operations).hasSizeGreaterThanOrEqualTo(12);

    List<String> failures = new ArrayList<>();
    for (Operation op : operations) {
      String path =
          op.path()
              .replace("{slug}", slug)
              .replace("{id}", UUID.randomUUID().toString())
              .replace("{target}", "MUNICIPALITY")
              .replace("{variant}", "large")
              .replaceAll("\\{[^}/]+}", UUID.randomUUID().toString());
      if (path.endsWith("/custom-fields")) {
        path = path + "?target=MUNICIPALITY";
      }
      Object body = op.method().equals("GET") ? null : Map.of();
      Api.Response response = api().method(op.method(), path, body).send();
      if (op.isPublic()) {
        if (response.status() == 401) {
          failures.add(op + " is @PublicEndpoint but answered 401");
        }
      } else if (response.status() != 401
          || !response.contentType().startsWith("application/problem+json")
          || !"auth.unauthenticated".equals(response.json().get("code").asString())) {
        failures.add(op + " without token answered " + response);
      }
    }
    assertThat(failures).isEmpty();
    System.err.printf(
        "EndpointSecurityIT: %d handlers = %d operations in openapi.json%n",
        operations.size(), contractOperations().size());
  }

  @Test
  void handlersMatchTheContract() throws IOException {
    TreeSet<String> handlers = new TreeSet<>();
    operations().forEach(op -> handlers.add(op.method() + " " + op.path()));
    assertThat(handlers).isEqualTo(contractOperations());
  }

  private List<Operation> operations() {
    List<Operation> result = new ArrayList<>();
    mapping
        .getHandlerMethods()
        .forEach(
            (RequestMappingInfo info, HandlerMethod handler) -> {
              String pkg = handler.getBeanType().getPackageName();
              if (!pkg.startsWith("gal.furar") || pkg.startsWith("gal.furar.testsupport")) {
                return;
              }
              if (pkg.startsWith("gal.furar.shared.web")) {
                return; // ErrorEndpoint (/error), oculto no contrato
              }
              boolean isPublic = handler.hasMethodAnnotation(PublicEndpoint.class);
              for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                  result.add(new Operation(method.name(), pattern, isPublic));
                }
              }
            });
    return result;
  }

  private static TreeSet<String> contractOperations() throws IOException {
    JsonNode contract = Api.JSON.readTree(Files.readString(Path.of("openapi.json")));
    TreeSet<String> result = new TreeSet<>();
    for (var path : contract.get("paths").properties()) {
      for (var method : path.getValue().properties()) {
        if (List.of("get", "post", "put", "patch", "delete").contains(method.getKey())) {
          result.add(method.getKey().toUpperCase(java.util.Locale.ROOT) + " " + path.getKey());
        }
      }
    }
    return result;
  }
}
