// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** O que cambiou nunha escritura (ADR-0020, revisión do 2026-11-10): curto e sen datos persoais. */
class AuditChangesTest {

  @Test
  void keepsOnlyTheFieldsThatChangedWithTheirValues() {
    var before =
        AuditChanges.snapshot()
            .value("season.name", "Inverno")
            .value("season.endDate", LocalDate.of(2027, 6, 30))
            .value("use.price", AuditChanges.money(1000));
    var after =
        AuditChanges.snapshot()
            .value("season.name", "Inverno")
            .value("season.endDate", LocalDate.of(2027, 6, 15))
            .value("use.price", AuditChanges.money(1250));

    assertThat(new AuditChanges().compare(before, after).list())
        .containsExactly(
            Map.of("field", "season.endDate", "before", "2027-06-30", "after", "2027-06-15"),
            Map.of("field", "use.price", "before", "10,00 €", "after", "12,50 €"));
  }

  @Test
  void hiddenFieldsSayOnlyThatTheyChanged() {
    var before = AuditChanges.snapshot().hidden("facility.phone", "981 000 000");
    var after = AuditChanges.snapshot().hidden("facility.phone", "981 111 111");

    List<Map<String, Object>> list = new AuditChanges().compare(before, after).list();

    assertThat(list).containsExactly(Map.of("field", "facility.phone", "hidden", true));
    assertThat(list.toString()).doesNotContain("981");
  }

  @Test
  void anAddedValueHasNoBeforeAndNothingChangedLeavesTheDetailsAlone() {
    AuditChanges changes = new AuditChanges().value("facility.address", null, "Rúa Nova, 1");
    assertThat(changes.list())
        .containsExactly(Map.of("field", "facility.address", "after", "Rúa Nova, 1"));

    Map<String, Object> details =
        new AuditChanges().value("x", "a", "a").into(new LinkedHashMap<>());
    assertThat(details).isEmpty();
  }

  @Test
  void longValuesAreCutAndTooManyChangesAreCounted() {
    String longText = "x".repeat(200);
    AuditChanges changes = new AuditChanges().value("legal.address", null, longText);
    assertThat((String) changes.list().get(0).get("after"))
        .hasSize(AuditChanges.VALUE_MAX)
        .endsWith("…");

    AuditChanges many = new AuditChanges();
    for (int i = 0; i < AuditChanges.ITEMS_MAX + 3; i++) {
      many.value("f" + i, null, "v");
    }
    assertThat(many.list()).hasSize(AuditChanges.ITEMS_MAX + 1);
    assertThat(many.list().get(AuditChanges.ITEMS_MAX))
        .isEqualTo(Map.of("field", AuditChanges.MORE, "after", "3"));
  }

  @Test
  void formatsClockRangesAndMoney() {
    assertThat(AuditChanges.range(9 * 60, 21 * 60 + 30)).isEqualTo("09:00–21:30");
    assertThat(AuditChanges.clock(1440)).isEqualTo("24:00");
    assertThat(AuditChanges.money(5)).isEqualTo("0,05 €");
    assertThat(AuditChanges.money(null)).isNull();
  }
}
