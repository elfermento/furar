// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import static gal.furar.customfields.domain.Definitions.define;
import static gal.furar.customfields.domain.Definitions.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.LocalizedText;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D18 (ADR-0011): cada tipo e cada regra do validador de valores. */
class CustomFieldValidatorTest {

  static Map<String, CustomFieldValue> ok(CustomFieldDefinition def, Object value) {
    Map<String, Object> raw = new HashMap<>();
    raw.put(def.code(), value);
    return ValuesValidator.validate(
        Definitions.TARGET, List.of(def), raw, CustomFieldValues.empty(), Audience.STAFF);
  }

  static List<String> errors(List<CustomFieldDefinition> defs, Map<String, ?> raw) {
    return errors(defs, raw, CustomFieldValues.empty(), Audience.STAFF);
  }

  static List<String> errors(
      List<CustomFieldDefinition> defs,
      Map<String, ?> raw,
      CustomFieldValues previous,
      Audience writer) {
    try {
      ValuesValidator.validate(Definitions.TARGET, defs, raw, previous, writer);
      return List.of();
    } catch (DomainException e) {
      assertThat(e.errorCode().code()).isEqualTo("validation.failed");
      return e.violations().stream().map(v -> v.field() + "=" + v.code()).toList();
    }
  }

  static String error(CustomFieldDefinition def, Object value) {
    Map<String, Object> raw = new HashMap<>();
    raw.put(def.code(), value);
    List<String> found = errors(List.of(def), raw);
    assertThat(found).hasSize(1);
    return found.get(0).substring(found.get(0).indexOf('=') + 1);
  }

  @Test
  void text() {
    var def = define("name_x", FieldType.TEXT, false, Definitions.text(2, 5, null), List.of());
    assertThat(ok(def, "ab")).containsEntry("name_x", new CustomFieldValue.Text("ab"));
    assertThat(ok(def, "ñññññ")).hasSize(1);
    assertThat(error(def, "a")).isEqualTo("customfield.too-short");
    assertThat(error(def, "abcdef")).isEqualTo("customfield.too-long");
    assertThat(error(def, 12)).isEqualTo("customfield.invalid-type");
    assertThat(ok(def, null)).isEmpty();
    var required = define("req", FieldType.TEXT, true);
    assertThat(error(required, "")).isEqualTo("customfield.required");
    assertThat(error(required, "x".repeat(501))).isEqualTo("customfield.too-long");
    var longText = define("bio", FieldType.LONG_TEXT, false);
    assertThat(ok(longText, "x".repeat(5000))).hasSize(1);
    assertThat(error(longText, "x".repeat(5001))).isEqualTo("customfield.too-long");
  }

  @Test
  void textFormats() {
    var email =
        define(
            "mail",
            FieldType.TEXT,
            false,
            Definitions.text(null, null, TextFormat.EMAIL),
            List.of());
    var phone =
        define(
            "tel",
            FieldType.TEXT,
            false,
            Definitions.text(null, null, TextFormat.PHONE),
            List.of());
    var url =
        define(
            "web", FieldType.TEXT, false, Definitions.text(null, null, TextFormat.URL), List.of());
    assertThat(ok(email, "xestor@naron.gal")).hasSize(1);
    assertThat(error(email, "xestor@naron")).isEqualTo("customfield.invalid-format");
    assertThat(ok(phone, "+34 981 000 000")).hasSize(1);
    assertThat(error(phone, "981-000")).isEqualTo("customfield.invalid-format");
    assertThat(ok(url, "https://naron.gal")).hasSize(1);
    assertThat(error(url, "ftp://naron.gal")).isEqualTo("customfield.invalid-format");
  }

  @Test
  void number() {
    var def =
        define("amount", FieldType.NUMBER, false, Definitions.number("0", "100", 2), List.of());
    assertThat(ok(def, new BigDecimal("99.99")))
        .containsEntry("amount", new CustomFieldValue.Number(new BigDecimal("99.99")));
    assertThat(ok(def, 3)).hasSize(1);
    assertThat(ok(def, new BigDecimal("1.500"))).hasSize(1);
    assertThat(error(def, new BigDecimal("-0.01"))).isEqualTo("customfield.below-min");
    assertThat(error(def, new BigDecimal("100.01"))).isEqualTo("customfield.above-max");
    assertThat(error(def, new BigDecimal("1.234"))).isEqualTo("customfield.too-many-decimals");
    assertThat(error(def, "12")).isEqualTo("customfield.invalid-type");
    assertThat(error(def, Double.NaN)).isEqualTo("customfield.invalid-type");
    assertThat(error(def, Float.POSITIVE_INFINITY)).isEqualTo("customfield.invalid-type");
    var integer =
        define("count", FieldType.NUMBER, false, Definitions.number(null, null, 0), List.of());
    assertThat(ok(integer, new BigDecimal("3.0"))).hasSize(1);
    assertThat(error(integer, new BigDecimal("3.5"))).isEqualTo("customfield.too-many-decimals");
    assertThat(ok(integer, new BigDecimal("1E+3"))).hasSize(1);
  }

  @Test
  void date() {
    var def =
        define(
            "day", FieldType.DATE, false, Definitions.date("2026-01-01", "2026-12-31"), List.of());
    assertThat(ok(def, "2026-02-28"))
        .containsEntry("day", new CustomFieldValue.Date(LocalDate.of(2026, 2, 28)));
    assertThat(error(def, "2026-02-30")).isEqualTo("customfield.invalid-date");
    assertThat(error(def, "28/02/2026")).isEqualTo("customfield.invalid-date");
    assertThat(error(def, "2025-12-31")).isEqualTo("customfield.date-too-early");
    assertThat(error(def, "2027-01-01")).isEqualTo("customfield.date-too-late");
    assertThat(error(def, 20260228)).isEqualTo("customfield.invalid-type");
  }

  @Test
  void bool() {
    var def = define("flag", FieldType.BOOLEAN, true);
    assertThat(ok(def, false)).containsEntry("flag", new CustomFieldValue.Bool(false));
    assertThat(error(def, "true")).isEqualTo("customfield.invalid-type");
    assertThat(error(def, null)).isEqualTo("customfield.required");
  }

  @Test
  void select() {
    var def = define("zone", FieldType.SELECT, false);
    assertThat(ok(def, "north")).containsEntry("zone", new CustomFieldValue.Choice("north"));
    assertThat(error(def, "old")).isEqualTo("customfield.invalid-option");
    assertThat(error(def, "west")).isEqualTo("customfield.invalid-option");
    assertThat(error(def, 1)).isEqualTo("customfield.invalid-type");
    CustomFieldValues previous = CustomFieldValues.fromStorage(Map.of("zone", "old"));
    assertThat(errors(List.of(def), Map.of("zone", "old"), previous, Audience.STAFF))
        .as("an archived option already chosen can be kept")
        .isEmpty();
  }

  @Test
  void multiSelect() {
    var def = define("zones", FieldType.MULTI_SELECT, false, Definitions.items(1, 2), options());
    assertThat(ok(def, List.of("north", "south")))
        .containsEntry("zones", new CustomFieldValue.Choices(List.of("north", "south")));
    assertThat(error(def, List.of())).isEqualTo("customfield.too-few-options");
    assertThat(error(def, List.of("north", "north"))).isEqualTo("customfield.duplicate-option");
    assertThat(error(def, List.of("north", "old"))).isEqualTo("customfield.invalid-option");
    assertThat(error(def, "north")).isEqualTo("customfield.invalid-type");
    assertThat(error(def, Arrays.asList("north", 1))).isEqualTo("customfield.invalid-type");
    var withExtra =
        define(
            "zones",
            FieldType.MULTI_SELECT,
            true,
            Definitions.items(null, 2),
            List.of(
                options().get(0),
                options().get(1),
                new FieldOption("east", new LocalizedText("Leste", "Este"), false)));
    assertThat(error(withExtra, List.of("north", "south", "east")))
        .isEqualTo("customfield.too-many-options");
    assertThat(error(withExtra, List.of())).isEqualTo("customfield.required");
  }

  @Test
  void unknownInvisibleAndArchivedFields() {
    var visible = define("web", FieldType.TEXT, false);
    var staffOnly =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            "secret",
            FieldType.TEXT,
            Definitions.spec(false, FieldRules.NONE, List.of(), Visibility.STAFF),
            Definitions.NOW);
    var archived = define("old_field", FieldType.TEXT, false).deactivate(Definitions.NOW);
    List<CustomFieldDefinition> defs = List.of(visible, staffOnly, archived);
    CustomFieldValues previous =
        CustomFieldValues.fromStorage(Map.of("secret", "keep", "old_field", "kept"));

    // Quen escribe é o titular: non ve "secret" e non pode escribilo, pero consérvase.
    Map<String, CustomFieldValue> result =
        ValuesValidator.validate(
            Definitions.TARGET, defs, Map.of("web", "https://a.gal"), previous, Audience.OWNER);
    assertThat(result)
        .containsEntry("secret", new CustomFieldValue.Text("keep"))
        .containsEntry("old_field", new CustomFieldValue.Text("kept"))
        .containsEntry("web", new CustomFieldValue.Text("https://a.gal"));

    assertThat(errors(defs, Map.of("secret", "x"), previous, Audience.OWNER))
        .containsExactly("customFields.secret=customfield.unknown-field");
    assertThat(errors(defs, Map.of("nope", 1)))
        .containsExactly("customFields.nope=customfield.unknown-field");
    assertThat(errors(defs, Map.of("old_field", "changed"), previous, Audience.STAFF))
        .containsExactly("customFields.old_field=customfield.inactive-field");
    assertThat(errors(defs, Map.of("old_field", "kept"), previous, Audience.STAFF)).isEmpty();
  }

  /**
   * Especificación 1b: o obrigatorio e a visibilidade son do uso do campo. Un campo que non se usa
   * no destino é descoñecido alí (un campo, un sitio: revisión do 2026-11-04).
   */
  @Test
  void requiredAndVisibilityComeFromTheUsageOfTheTarget() {
    CustomFieldTarget facility = new CustomFieldTarget("FACILITY");
    var phone =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            "phone",
            FieldType.TEXT,
            Definitions.spec(
                FieldRules.NONE,
                List.of(),
                false,
                new FieldUsage(Definitions.TARGET, true, Visibility.STAFF)),
            Definitions.NOW);
    var elsewhere =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            "court",
            FieldType.TEXT,
            Definitions.spec(
                FieldRules.NONE,
                List.of(),
                false,
                new FieldUsage(facility, true, Visibility.PUBLIC)),
            Definitions.NOW);
    List<CustomFieldDefinition> defs = List.of(phone, elsewhere);
    assertThat(errors(defs, Map.of())).containsExactly("customFields.phone=customfield.required");
    assertThat(errors(defs, Map.of("phone", "981", "court", "A")))
        .containsExactly("customFields.court=customfield.unknown-field");
    assertThat(
            ValuesValidator.validate(
                facility, defs, Map.of("court", "A"), CustomFieldValues.empty(), Audience.PUBLIC))
        .containsOnlyKeys("court");
    // No concello, "phone" é só do persoal: o titular non o ve nin o escribe.
    assertThat(errors(defs, Map.of("phone", "981"), CustomFieldValues.empty(), Audience.OWNER))
        .containsExactly("customFields.phone=customfield.unknown-field");
    CustomFieldValues stored = CustomFieldValues.fromStorage(Map.of("phone", "981", "court", "A"));
    assertThat(ValueProjector.project(facility, defs, stored, Audience.PUBLIC))
        .containsOnlyKeys("court");
    assertThat(ValueProjector.project(Definitions.TARGET, defs, stored, Audience.PUBLIC)).isEmpty();
  }

  @Test
  void sizeLimit() {
    CustomFieldValues small = CustomFieldValues.fromStorage(Map.of("a", "x".repeat(100)));
    ValuesValidator.checkSize(small);
    CustomFieldValues big =
        CustomFieldValues.fromStorage(Map.of("a", "x".repeat(ValuesValidator.MAX_BYTES)));
    assertThatThrownBy(() -> ValuesValidator.checkSize(big))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CustomFieldError.LIMIT_EXCEEDED));
  }

  @Test
  void projectorTypesAndFiltersByAudience() {
    var day = define("day", FieldType.DATE, false);
    var zone = define("zone", FieldType.SELECT, false);
    var secret =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            "secret",
            FieldType.TEXT,
            Definitions.spec(false, FieldRules.NONE, List.of(), Visibility.STAFF),
            Definitions.NOW);
    CustomFieldValues stored =
        CustomFieldValues.fromStorage(
            Map.of(
                "day",
                "2026-02-28",
                "zone",
                "north",
                "secret",
                "s",
                "orphan",
                "o",
                "bad_day",
                "x"));
    var bad = define("bad_day", FieldType.DATE, false);
    Map<String, CustomFieldValue> anonymous =
        ValueProjector.project(
            Definitions.TARGET, List.of(day, zone, secret, bad), stored, Audience.PUBLIC);
    assertThat(anonymous)
        .containsEntry("day", new CustomFieldValue.Date(LocalDate.of(2026, 2, 28)))
        .containsEntry("zone", new CustomFieldValue.Choice("north"))
        .containsEntry("bad_day", new CustomFieldValue.Text("x"))
        .doesNotContainKeys("secret", "orphan");
    assertThat(ValueProjector.project(Definitions.TARGET, List.of(secret), stored, Audience.STAFF))
        .containsKey("secret");
  }

  @Test
  void violationsKeepFieldPath() {
    FieldViolation v = FieldViolation.of("customFields.x", CustomFieldError.REQUIRED);
    assertThat(v.code()).isEqualTo("customfield.required");
  }

  /**
   * ADR-0018: na importación valen os valores gardados aínda que as regras cambiasen (campo agora
   * obrigatorio, máximo máis baixo, opción ou campo arquivados), pero non claves sen definición,
   * tipos errados nin opcións inventadas.
   */
  @Test
  void restoreAcceptsStoredValuesButNotInventedOnes() {
    var shortText =
        define("nick", FieldType.TEXT, true, Definitions.text(null, 3, null), List.of());
    var zone = define("zone", FieldType.SELECT, false);
    var zones = define("zones", FieldType.MULTI_SELECT, false);
    var archived = define("legacy", FieldType.NUMBER, false).deactivate(Definitions.NOW);
    var day = define("day", FieldType.DATE, false);
    List<CustomFieldDefinition> defs = List.of(shortText, zone, zones, archived, day);

    Map<String, CustomFieldValue> restored =
        ValuesValidator.restore(
            Definitions.TARGET,
            defs,
            Map.of(
                "nick", "longer than three",
                "zone", "old",
                "zones", List.of("north", "old"),
                "legacy", 12.5,
                "day", "2025-02-28"));
    assertThat(restored)
        .containsEntry("nick", new CustomFieldValue.Text("longer than three"))
        .containsEntry("zone", new CustomFieldValue.Choice("old"))
        .containsEntry("zones", new CustomFieldValue.Choices(List.of("north", "old")))
        .containsEntry("legacy", new CustomFieldValue.Number(new java.math.BigDecimal("12.5")))
        .hasSize(5);
    assertThat(ValuesValidator.restore(Definitions.TARGET, defs, Map.of())).isEmpty();

    assertThat(restoreErrors(defs, Map.of("ghost", "x")))
        .containsExactly("customFields.ghost=customfield.unknown-field");
    assertThat(restoreErrors(defs, Map.of("zone", "invented")))
        .containsExactly("customFields.zone=customfield.invalid-option");
    assertThat(restoreErrors(defs, Map.of("zones", List.of("north", "north"))))
        .containsExactly("customFields.zones=customfield.duplicate-option");
    assertThat(restoreErrors(defs, Map.of("legacy", "12")))
        .containsExactly("customFields.legacy=customfield.invalid-type");
    assertThat(restoreErrors(defs, Map.of("day", "2025-02-30")))
        .containsExactly("customFields.day=customfield.invalid-date");
  }

  private static List<String> restoreErrors(List<CustomFieldDefinition> defs, Map<String, ?> raw) {
    try {
      ValuesValidator.restore(Definitions.TARGET, defs, raw);
      return List.of();
    } catch (DomainException e) {
      assertThat(e.errorCode().code()).isEqualTo("validation.failed");
      return e.violations().stream().map(v -> v.field() + "=" + v.code()).toList();
    }
  }
}
