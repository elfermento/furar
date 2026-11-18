// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * D17 (ADR-0009): o OpenAPI que xera springdoc é determinista e igual ao {@code api/openapi.json}
 * versionado. Para rexeneralo: {@code -Dfurar.openapi.write=true} (o único modo que escribe).
 */
class OpenApiContractIT extends AbstractIT {

  static final Path CONTRACT = Path.of("openapi.json");
  static final String REGENERATE =
      "(cd api && ./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false"
          + " -Dit.test=OpenApiContractIT -Dfurar.openapi.write=true)";
  static final JsonMapper SORTED =
      JsonMapper.builder()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  @Test
  void contractIsUpToDate() throws IOException {
    Api.Response first = api().get("/api/v1/openapi.json").send();
    Api.Response second = api().get("/api/v1/openapi.json").send();
    assertThat(first.status()).isEqualTo(200);
    String generated = normalize(first.json());
    assertThat(normalize(second.json())).as("OpenAPI must be deterministic").isEqualTo(generated);

    if (Boolean.getBoolean("furar.openapi.write")) {
      Files.writeString(CONTRACT, generated, StandardCharsets.UTF_8);
      System.err.println("OpenApiContractIT: wrote " + CONTRACT.toAbsolutePath());
      return;
    }
    if (!Files.exists(CONTRACT)) {
      fail("api/openapi.json is missing. Generate it with:%n  %s", REGENERATE);
    }
    String committed = normalize(Api.JSON.readTree(Files.readString(CONTRACT)));
    if (!committed.equals(generated)) {
      List<String> diff = new ArrayList<>();
      diff(Api.JSON.readTree(committed), Api.JSON.readTree(generated), "", diff);
      fail(
          "api/openapi.json is out of date (%d differences):%n  %s%nRegenerate and review the diff:%n  %s",
          diff.size(), String.join("\n  ", diff.subList(0, Math.min(40, diff.size()))), REGENERATE);
    }
  }

  @Test
  void problemCodeIsAnEnumWithEveryErrorCode() {
    JsonNode problem =
        api()
            .get("/api/v1/openapi.json")
            .send()
            .json()
            .at("/components/schemas/Problem/properties/code");
    List<String> codes = new ArrayList<>();
    problem.get("enum").forEach(n -> codes.add(n.asString()));
    assertThat(codes).contains("municipality.not-found", "auth.forbidden", "customfield.required");
    assertThat(codes).isEqualTo(ErrorCatalog.scan().stream().map(c -> c.code()).toList());
  }

  /** JSON con claves ordenadas, sangrado de 2 e salto de liña final. */
  static String normalize(JsonNode node) {
    Object tree = SORTED.convertValue(node, Object.class);
    return SORTED.writeValueAsString(tree).replace("\r\n", "\n") + "\n";
  }

  private static void diff(JsonNode a, JsonNode b, String path, List<String> out) {
    if (a.equals(b)) {
      return;
    }
    if (a.isObject() && b.isObject()) {
      var names = new java.util.TreeSet<String>();
      a.propertyNames().forEach(names::add);
      b.propertyNames().forEach(names::add);
      for (String name : names) {
        String child = path + "/" + name.replace("~", "~0").replace("/", "~1");
        if (!a.has(name)) {
          out.add("+ " + child);
        } else if (!b.has(name)) {
          out.add("- " + child);
        } else {
          diff(a.get(name), b.get(name), child, out);
        }
      }
      return;
    }
    out.add("~ " + (path.isEmpty() ? "/" : path) + ": " + abbreviate(a) + " -> " + abbreviate(b));
  }

  private static String abbreviate(JsonNode n) {
    String s = n.toString();
    return s.length() > 80 ? s.substring(0, 77) + "..." : s;
  }
}
