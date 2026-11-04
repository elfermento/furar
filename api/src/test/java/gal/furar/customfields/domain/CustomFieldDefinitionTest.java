// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import static gal.furar.customfields.domain.Definitions.NOW;
import static gal.furar.customfields.domain.Definitions.define;
import static gal.furar.customfields.domain.Definitions.options;
import static gal.furar.customfields.domain.Definitions.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.LocalizedText;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class CustomFieldDefinitionTest {

  @Test
  void createsActiveDefinitions() {
    CustomFieldDefinition d = define("ridega_code", FieldType.TEXT, true);
    assertThat(d.isActive()).isTrue();
    assertThat(d.version()).isNull();
    assertThat(d.targets()).containsExactly(CustomFieldTarget.MUNICIPALITY);
    assertThat(d.code()).isEqualTo("ridega_code");
    assertThat(d.usage(CustomFieldTarget.MUNICIPALITY).orElseThrow().required()).isTrue();
    assertThat(d.usedIn(new CustomFieldTarget("FACILITY"))).isFalse();
    assertThat(d.label().gl()).isEqualTo("Etiqueta");
    assertThat(d.createdAt()).isEqualTo(NOW).isEqualTo(d.updatedAt());
    assertThat(define("zone", FieldType.SELECT, false).activeOptionCodes())
        .containsExactly("north", "south");
  }

  @Test
  void codeMustBeValidAndNotReserved() {
    for (String bad : List.of("A", "1abc", "a", "with-dash", "x".repeat(41))) {
      assertError(() -> define(bad, FieldType.TEXT, false), CustomFieldError.CODE_INVALID);
    }
    assertError(() -> define(null, FieldType.TEXT, false), CustomFieldError.CODE_INVALID);
    assertError(() -> define("version", FieldType.TEXT, false), CustomFieldError.CODE_RESERVED);
    // Os núcleo de cada entidade (slug do concello) compróbaos o caso de uso co seu provider.
    assertThat(ReservedCodes.isReserved("slug")).isFalse();
    assertThat(ReservedCodes.isReserved("id")).isTrue();
  }

  @Test
  void optionsAndRulesMustFitTheType() {
    assertError(
        () -> define("zone", FieldType.SELECT, false, FieldRules.NONE, List.of()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("zone", FieldType.SELECT, false, FieldRules.NONE, List.of(options().get(2))),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("name_x", FieldType.TEXT, false, FieldRules.NONE, options()),
        CustomFieldError.INVALID_DEFINITION);
    var duplicated = List.of(options().get(0), options().get(0));
    assertError(
        () -> define("zone", FieldType.SELECT, false, FieldRules.NONE, duplicated),
        CustomFieldError.INVALID_DEFINITION);
    for (var invalid :
        List.of(
            Definitions.number("1", null, null),
            Definitions.text(5, 2, null),
            Definitions.text(null, 501, null),
            Definitions.text(-1, null, null),
            Definitions.text(null, 0, null),
            Definitions.date("2026-01-01", null),
            Definitions.items(1, null),
            Definitions.text(null, null, TextFormat.EMAIL))) {
      assertError(
          () -> define("bool_x", FieldType.BOOLEAN, false, invalid, List.of()),
          CustomFieldError.INVALID_DEFINITION);
    }
    assertError(
        () -> define("num", FieldType.NUMBER, false, Definitions.number("5", "1", null), List.of()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("num", FieldType.NUMBER, false, Definitions.number(null, null, 7), List.of()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () ->
            define(
                "day",
                FieldType.DATE,
                false,
                Definitions.date("2026-02-01", "2026-01-01"),
                List.of()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("many", FieldType.MULTI_SELECT, false, Definitions.items(3, 1), options()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("many", FieldType.MULTI_SELECT, false, Definitions.items(3, null), options()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("many", FieldType.MULTI_SELECT, false, Definitions.items(-1, null), options()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () -> define("many", FieldType.MULTI_SELECT, false, Definitions.items(null, 0), options()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () ->
            define(
                "long", FieldType.LONG_TEXT, false, Definitions.text(null, 5001, null), List.of()),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () ->
            define(
                "txt",
                FieldType.LONG_TEXT,
                false,
                Definitions.text(null, null, TextFormat.URL),
                List.of()),
        CustomFieldError.INVALID_DEFINITION);
    assertThat(
            define(
                "long", FieldType.LONG_TEXT, false, Definitions.text(null, 5000, null), List.of()))
        .isNotNull();
  }

  @Test
  void optionCodesAreValidated() {
    assertError(
        () -> new FieldOption("Bad Code", new LocalizedText("a", "a"), false),
        CustomFieldError.INVALID_DEFINITION);
    assertThat(options().get(0).deactivate().inactive()).isTrue();
  }

  @Test
  void updateKeepsIdentityAndMayDropOptions() {
    CustomFieldDefinition d = define("zone", FieldType.SELECT, false);
    var withNew =
        List.of(
            options().get(0).deactivate(),
            options().get(1),
            options().get(2),
            new FieldOption("east", new LocalizedText("Leste", "Este"), false));
    CustomFieldDefinition updated =
        d.update(spec(true, FieldRules.NONE, withNew, Visibility.STAFF), NOW.plusSeconds(1));
    assertThat(updated.id()).isEqualTo(d.id());
    assertThat(updated.code()).isEqualTo("zone");
    assertThat(updated.activeOptionCodes()).containsExactly("south", "east");
    assertThat(updated.usage(CustomFieldTarget.MUNICIPALITY).orElseThrow().visibility())
        .isEqualTo(Visibility.STAFF);
    assertThat(updated.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    assertThat(updated.hasActiveOption("north")).isFalse();

    // Quitar opcións vale no dominio; se teñen valores, o caso de uso rexéitao.
    DefinitionSpec onlyNorth =
        spec(false, FieldRules.NONE, List.of(options().get(0)), Visibility.PUBLIC);
    assertThat(d.update(onlyNorth, NOW).activeOptionCodes()).containsExactly("north");
    assertThat(d.removedOptions(onlyNorth)).containsExactlyInAnyOrder("south", "old");
  }

  /**
   * AP-03 (RGPD): un campo marcado como dato persoal nunca é PUBLIC, nin ao crear nin ao editar.
   */
  @Test
  void personalDataIsNeverPublic() {
    assertError(
        () ->
            CustomFieldDefinition.create(
                CustomFieldDefinitionId.newId(),
                "allergies",
                FieldType.TEXT,
                personal(Visibility.PUBLIC),
                NOW),
        CustomFieldError.PERSONAL_DATA_PUBLIC);
    CustomFieldDefinition owner =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            "allergies",
            FieldType.TEXT,
            personal(Visibility.OWNER),
            NOW);
    assertThat(owner.spec().personalData()).isTrue();
    assertError(
        () -> owner.update(personal(Visibility.PUBLIC), NOW),
        CustomFieldError.PERSONAL_DATA_PUBLIC);
    assertThat(owner.update(personal(Visibility.STAFF), NOW).personalData()).isTrue();
    // Nun segundo sitio tampouco pode ser público.
    assertError(
        () ->
            owner.update(
                Definitions.spec(
                    FieldRules.NONE,
                    List.of(),
                    true,
                    new FieldUsage(RESIDENT, false, Visibility.STAFF),
                    new FieldUsage(CustomFieldTarget.MUNICIPALITY, false, Visibility.PUBLIC)),
                NOW),
        CustomFieldError.PERSONAL_DATA_PUBLIC);
  }

  static final CustomFieldTarget RESIDENT = new CustomFieldTarget("RESIDENT_PROFILE");

  private static DefinitionSpec personal(Visibility visibility) {
    return Definitions.spec(
        FieldRules.NONE, List.of(), true, new FieldUsage(RESIDENT, false, visibility));
  }

  /** Un campo, un sitio (revisión do 2026-11-04). */
  @Test
  void aFieldHasExactlyOneUsage() {
    assertError(
        () ->
            CustomFieldDefinition.create(
                CustomFieldDefinitionId.newId(),
                "nowhere",
                FieldType.TEXT,
                Definitions.spec(FieldRules.NONE, List.of(), false),
                NOW),
        CustomFieldError.INVALID_DEFINITION);
    assertError(
        () ->
            CustomFieldDefinition.create(
                CustomFieldDefinitionId.newId(),
                "twice",
                FieldType.TEXT,
                Definitions.spec(
                    FieldRules.NONE,
                    List.of(),
                    false,
                    new FieldUsage(RESIDENT, true, Visibility.OWNER),
                    new FieldUsage(CustomFieldTarget.MUNICIPALITY, false, Visibility.PUBLIC)),
                NOW),
        CustomFieldError.SINGLE_USAGE);
    CustomFieldDefinition phone =
        CustomFieldDefinition.create(
            CustomFieldDefinitionId.newId(),
            "phone",
            FieldType.TEXT,
            Definitions.spec(
                FieldRules.NONE,
                List.of(),
                false,
                new FieldUsage(RESIDENT, true, Visibility.OWNER)),
            NOW);
    assertThat(phone.target()).isEqualTo(RESIDENT);
    assertThat(phone.usage(RESIDENT).orElseThrow().required()).isTrue();
    DefinitionSpec elsewhere =
        Definitions.spec(
            FieldRules.NONE,
            List.of(),
            false,
            new FieldUsage(CustomFieldTarget.MUNICIPALITY, false, Visibility.STAFF));
    assertThat(phone.targetChanged(elsewhere)).isTrue();
    assertThat(phone.update(elsewhere, NOW).target()).isEqualTo(CustomFieldTarget.MUNICIPALITY);
    assertThat(phone.targetChanged(personal(Visibility.STAFF))).isFalse();
    assertThat(new FieldUsage(RESIDENT, false, null).visibility()).isEqualTo(Visibility.STAFF);
  }

  @Test
  void deactivateAndActivateAreIdempotent() {
    CustomFieldDefinition d = define("web", FieldType.TEXT, false);
    CustomFieldDefinition inactive = d.deactivate(NOW.plusSeconds(5));
    assertThat(inactive.status()).isEqualTo(DefinitionStatus.INACTIVE);
    assertThat(inactive.deactivate(NOW)).isSameAs(inactive);
    assertThat(inactive.activate(NOW).isActive()).isTrue();
    assertThat(d.activate(NOW)).isSameAs(d);
  }

  @Test
  void visibilityByAudience() {
    assertThat(Visibility.PUBLIC.visibleTo(Audience.PUBLIC)).isTrue();
    assertThat(Visibility.OWNER.visibleTo(Audience.PUBLIC)).isFalse();
    assertThat(Visibility.OWNER.visibleTo(Audience.OWNER)).isTrue();
    assertThat(Visibility.OWNER.visibleTo(Audience.STAFF)).isTrue();
    assertThat(Visibility.STAFF.visibleTo(Audience.OWNER)).isFalse();
    assertThat(Visibility.STAFF.visibleTo(Audience.STAFF)).isTrue();
  }

  static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }
}
