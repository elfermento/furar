// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.CustomFieldValues; // @cf
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map; // @cf
import org.junit.jupiter.api.Test;

/** Invariantes do agregado, sen Spring. */
class @Name@Test {

  static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  static @Name@ create(String name) {
    return @Name@.create(
        @Name@Id.newId(),
        name,
        CustomFieldValues.empty(), // @cf
        NOW);
  }

  @Test
  void createsWithATrimmedName() {
    @Name@ created = create("  Primeiro  ");
    assertThat(created.name()).isEqualTo("Primeiro");
    assertThat(created.version()).isNull();
    assertThat(created.createdAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsBlankOrLongNames() {
    for (String name : new String[] {null, " ", "x".repeat(@Name@.NAME_MAX + 1)}) {
      assertThatThrownBy(() -> create(name))
          .isInstanceOfSatisfying(
              DomainException.class,
              e -> assertThat(e.errorCode()).isEqualTo(@Name@Error.NAME_INVALID));
    }
  }

  @Test
  void updateKeepsIdentityAndVersion() {
    @Name@ original =
        @Name@.rehydrate(
            @Name@Id.newId(),
            "Vello",
            CustomFieldValues.empty(), // @cf
            3,
            NOW,
            NOW);
    @Name@ updated =
        original.update(
            "Novo",
            CustomFieldValues.fromStorage(Map.of("code", "A1")), // @cf
            NOW.plusSeconds(60));
    assertThat(updated.id()).isEqualTo(original.id());
    assertThat(updated.version()).isEqualTo(3);
    assertThat(updated.name()).isEqualTo("Novo");
    assertThat(updated.customFields().toJson()).containsEntry("code", "A1"); // @cf
    assertThat(updated.updatedAt()).isEqualTo(NOW.plusSeconds(60));
  }
}
