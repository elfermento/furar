// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ADR-0018, versión 2: unha fila v1 de {@code customfields} pasa a ter un único uso. */
class CustomFieldsDataSectionTest {

  private final CustomFieldsDataSection section = new CustomFieldsDataSection(null, null);

  @Test
  void aVersion1RowBecomesASingleUsage() {
    Map<String, Object> v1 = new LinkedHashMap<>();
    v1.put("id", "019a1700-0000-7000-8000-000000000011");
    v1.put("target", "MUNICIPALITY");
    v1.put("code", "zone");
    v1.put("required", true);
    v1.put("visibility", "STAFF");
    v1.put("position", 3);
    v1.put("group", Map.of("code", "general"));
    v1.put("appliesWhen", Map.of("other", List.of("a")));
    v1.put("personalData", false);

    Map<String, Object> v2 = section.upgrade(1, v1);

    assertThat(v2)
        .containsEntry("id", "019a1700-0000-7000-8000-000000000011")
        .containsEntry("code", "zone")
        .containsEntry("personalData", false)
        .doesNotContainKeys("target", "required", "visibility", "position", "group", "appliesWhen")
        .containsEntry(
            "usages",
            List.of(Map.of("target", "MUNICIPALITY", "required", true, "visibility", "STAFF")));
    assertThat(v1).as("a fila orixinal non cambia").containsKey("target");
  }

  @Test
  void withoutRequiredTheUsageIsOptional() {
    Map<String, Object> v2 =
        section.upgrade(1, Map.of("target", "MUNICIPALITY", "visibility", "PUBLIC"));

    assertThat(v2.get("usages"))
        .isEqualTo(
            List.of(Map.of("target", "MUNICIPALITY", "required", false, "visibility", "PUBLIC")));
  }

  /** v2 → v3: arquivado pasa a desactivado, no campo e nas opcións. */
  @Test
  void archivedBecomesInactive() {
    Map<String, Object> v2 =
        Map.of(
            "status",
            "ARCHIVED",
            "options",
            List.of(
                Map.of("code", "north", "archived", true),
                Map.of("code", "south", "archived", false),
                Map.of("code", "east")));

    Map<String, Object> v3 = section.upgrade(2, v2);

    assertThat(v3).containsEntry("status", "INACTIVE");
    assertThat(v3.get("options"))
        .isEqualTo(
            List.of(
                Map.of("code", "north", "inactive", true),
                Map.of("code", "south", "inactive", false),
                Map.of("code", "east", "inactive", false)));
    assertThat(section.upgrade(2, Map.of("status", "ACTIVE"))).containsEntry("status", "ACTIVE");
  }
}
