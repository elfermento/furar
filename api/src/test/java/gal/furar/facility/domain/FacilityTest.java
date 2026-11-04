// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Invariantes do agregado, sen Spring. */
class FacilityTest {

  static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  static Address address() {
    return new Address(
        "Rúa do Alto",
        "12",
        "15570",
        "Narón",
        "15054",
        new BigDecimal("43.5167"),
        new BigDecimal("-8.1833"));
  }

  static Facility create(String name) {
    return Facility.create(FacilityId.newId(), name, address(), NOW);
  }

  @Test
  void createsActiveWithATrimmedName() {
    Facility created = create("  Campo de fútbol 1 de O Alto  ");
    assertThat(created.name()).isEqualTo("Campo de fútbol 1 de O Alto");
    assertThat(created.status()).isEqualTo(FacilityStatus.ACTIVE);
    assertThat(created.version()).isNull();
    assertThat(created.createdAt()).isEqualTo(NOW);
    assertThat(created.address()).isEqualTo(address());
  }

  @Test
  void rejectsBlankOrLongNames() {
    for (String name : new String[] {null, " ", "x".repeat(Facility.NAME_MAX + 1)}) {
      assertThatThrownBy(() -> create(name))
          .isInstanceOfSatisfying(
              DomainException.class,
              e -> assertThat(e.errorCode()).isEqualTo(FacilityError.NAME_INVALID));
    }
  }

  @Test
  void renameKeepsIdentityStatusAndVersion() {
    Facility original =
        Facility.rehydrate(
            FacilityId.newId(),
            "Vello",
            FacilityStatus.TEMPORARILY_CLOSED,
            address(),
            true,
            FacilityDetails.EMPTY,
            null,
            null,
            FacilityPaymentNotes.NONE,
            Set.of(),
            3,
            NOW,
            NOW);
    Facility renamed = original.rename("Novo", NOW.plusSeconds(60));
    assertThat(renamed.id()).isEqualTo(original.id());
    assertThat(renamed.version()).isEqualTo(3);
    assertThat(renamed.status()).isEqualTo(FacilityStatus.TEMPORARILY_CLOSED);
    assertThat(renamed.name()).isEqualTo("Novo");
    assertThat(renamed.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThatThrownBy(() -> original.rename(" ", NOW)).isInstanceOf(DomainException.class);
  }

  @Test
  void changeStatusKeepsTheRest() {
    Facility original = create("Pista de pádel 1");
    Facility withdrawn = original.changeStatus(FacilityStatus.WITHDRAWN, NOW.plusSeconds(1));
    assertThat(withdrawn.status()).isEqualTo(FacilityStatus.WITHDRAWN);
    assertThat(withdrawn.name()).isEqualTo(original.name());
    assertThat(withdrawn.id()).isEqualTo(original.id());
    assertThat(withdrawn.updatedAt()).isEqualTo(NOW.plusSeconds(1));
  }

  @Test
  void aDraftNeedsOnlyTheNameAndIsBornUnpublished() {
    Facility draft = Facility.create(FacilityId.newId(), "Pavillón", null, NOW);
    assertThat(draft.address()).isNull();
    assertThat(draft.published()).isFalse();
    assertThat(create("Con enderezo").published()).isFalse();
  }

  @Test
  void publishingNeedsTheAddress() {
    Facility draft = Facility.create(FacilityId.newId(), "Pavillón", null, NOW);
    assertThatThrownBy(() -> draft.publish(NOW))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FacilityError.NOT_PUBLISHABLE));
    Facility published = draft.relocate(address(), NOW).publish(NOW.plusSeconds(1));
    assertThat(published.published()).isTrue();
    assertThat(published.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    assertThat(published.publish(NOW.plusSeconds(2))).isSameAs(published);
  }
}
