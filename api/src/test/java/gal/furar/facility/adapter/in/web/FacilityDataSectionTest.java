// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unha fila dun ficheiro ata a v3 pasa á forma actual (ADR-0018). */
class FacilityDataSectionTest {

  final FacilityDataSection section = new FacilityDataSection(null, null);

  @Test
  void anAddressUpToVersion3LosesItsOldCode() {
    Map<String, Object> v3 =
        Map.of("name", "Pavillón", "address", Map.of("street", "Rúa Real", "ineCode", "15036"));

    Map<String, Object> now = section.upgrade(3, v3);

    assertThat(now).containsEntry("name", "Pavillón");
    assertThat(now.get("address")).isEqualTo(Map.of("street", "Rúa Real"));
  }

  @Test
  void aDraftWithoutAddressStaysTheSame() {
    Map<String, Object> draft = Map.of("name", "Borrador");

    assertThat(section.upgrade(1, draft)).isEqualTo(draft);
  }
}
