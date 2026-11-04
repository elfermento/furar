// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomFieldValuesTest {

  @Test
  void fromStorageReadsEveryJsonShape() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("text", "ola");
    json.put("int", 3);
    json.put("decimal", new BigDecimal("1.50"));
    json.put("double", 2.25d);
    json.put("flag", true);
    json.put("list", List.of("a", "b"));
    json.put("missing", null);
    CustomFieldValues values = CustomFieldValues.fromStorage(json);
    assertThat(values.asMap())
        .containsEntry("text", new CustomFieldValue.Text("ola"))
        .containsEntry("int", new CustomFieldValue.Number(new BigDecimal("3")))
        .containsEntry("decimal", new CustomFieldValue.Number(new BigDecimal("1.5")))
        .containsEntry("double", new CustomFieldValue.Number(new BigDecimal("2.25")))
        .containsEntry("flag", new CustomFieldValue.Bool(true))
        .containsEntry("list", new CustomFieldValue.Choices(List.of("a", "b")))
        .doesNotContainKey("missing");
    assertThat(CustomFieldValues.fromStorage(null)).isEqualTo(CustomFieldValues.empty());
    assertThatThrownBy(() -> CustomFieldValues.fromStorage(Map.of("x", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toJsonIsSortedAndTyped() {
    CustomFieldValues values =
        CustomFieldValues.trusted(
            Map.of(
                "b_date", new CustomFieldValue.Date(LocalDate.of(2026, 2, 28)),
                "a_choice", new CustomFieldValue.Choice("north")));
    assertThat(values.toJson().keySet()).containsExactly("a_choice", "b_date");
    assertThat(values.toJson()).containsEntry("b_date", "2026-02-28");
    assertThat(values.get("a_choice")).contains(new CustomFieldValue.Choice("north"));
    assertThat(values.isEmpty()).isFalse();
  }

  @Test
  void jsonSizeMatchesUtf8CompactJson() {
    CustomFieldValues values =
        CustomFieldValues.trusted(
            Map.of(
                "t", new CustomFieldValue.Text("ñ\"\\\n\u0001"),
                "n", new CustomFieldValue.Number(new BigDecimal("1E+3")),
                "l", new CustomFieldValue.Choices(List.of("x")),
                "b", new CustomFieldValue.Bool(false)));
    String expected = "{\"b\":false,\"l\":[\"x\"],\"n\":1000,\"t\":\"ñ\\\"\\\\\\n\\u0001\"}";
    assertThat(values.jsonSizeInBytes())
        .isEqualTo(expected.getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  void numbersCompareByValue() {
    assertThat(new CustomFieldValue.Number(new BigDecimal("2.0")))
        .isEqualTo(new CustomFieldValue.Number(new BigDecimal("2")))
        .hasSameHashCodeAs(new CustomFieldValue.Number(new BigDecimal("2.00")));
    assertThat(CustomFieldValues.empty().toString()).contains("CustomFieldValues");
  }
}
