// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Invariantes da persoa a cargo (ADR-0031, punto 1): ano, declaración e desactivación. */
class DependentTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

  private static Dependent create(String name, int birthYear, boolean inMyCare) {
    return Dependent.create(DependentId.newId(), "sub-ana", name, birthYear, inMyCare, NOW);
  }

  @Test
  void keepsTheNameTheYearAndWhenItWasDeclared() {
    Dependent dependent = create("  Uxía   Rey Souto ", 2016, true);

    assertThat(dependent.name()).isEqualTo("Uxía Rey Souto");
    assertThat(dependent.birthYear()).isEqualTo(2016);
    assertThat(dependent.declaredAt()).isEqualTo(NOW);
    assertThat(dependent.isActive()).isTrue();
    assertThat(dependent.version()).isNull();
  }

  @Test
  void theDeclarationIsMandatoryWhenCreatingAndWhenEditing() {
    assertThatThrownBy(() -> create("Uxía Rey", 2016, false))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PeopleError.DEPENDENT_DECLARATION_REQUIRED));
    Dependent saved = create("Uxía Rey", 2016, true);
    assertThatThrownBy(() -> saved.update("Uxía Rey", 2016, false, NOW))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PeopleError.DEPENDENT_DECLARATION_REQUIRED));
  }

  @Test
  void theBirthYearGoesFromThisYearMinus120ToThisYear() {
    assertThat(create("Avoa", 1906, true).birthYear()).isEqualTo(1906);
    assertThat(create("Bebé", 2026, true).birthYear()).isEqualTo(2026);
    for (int year : new int[] {1905, 2027, 0}) {
      assertThatThrownBy(() -> create("Alguén", year, true))
          .as("ano %s", year)
          .isInstanceOfSatisfying(
              DomainException.class,
              e -> assertThat(e.errorCode()).isEqualTo(PeopleError.DEPENDENT_BIRTH_YEAR_INVALID));
    }
  }

  @Test
  void editingDeclaresAgainAndKeepsIdentityAndVersion() {
    Dependent stored =
        Dependent.rehydrate(
            DependentId.newId(), "sub-ana", "Uxia Rey", 2016, NOW, null, 3, NOW, NOW);
    Instant later = NOW.plusSeconds(3600);

    Dependent edited = stored.update("Uxía Rey", 2015, true, later);

    assertThat(edited.id()).isEqualTo(stored.id());
    assertThat(edited.version()).isEqualTo(3);
    assertThat(edited.name()).isEqualTo("Uxía Rey");
    assertThat(edited.birthYear()).isEqualTo(2015);
    assertThat(edited.declaredAt()).isEqualTo(later);
    assertThat(edited.createdAt()).isEqualTo(NOW);
  }

  @Test
  void aDeactivatedRecordIsNotEditedAnyMore() {
    Dependent deactivated = create("Uxía Rey", 2016, true).deactivate(NOW);

    assertThat(deactivated.isActive()).isFalse();
    assertThat(deactivated.deactivatedAt()).isEqualTo(NOW);
    assertThatThrownBy(() -> deactivated.update("Uxía Rey", 2016, true, NOW))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PeopleError.DEPENDENT_NOT_FOUND));
  }
}
