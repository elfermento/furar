// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.domain.BookingError;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unha entidade e os seus responsables (ADR-0039, punto 7). */
class OrganizationTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  private static Organization club(List<String> managers) {
    return Organization.create(
        UUID.randomUUID(),
        "  Club Demo Baloncesto ",
        "Ana Rey",
        "club@example.org",
        "981 000 000",
        managers,
        "sub-xestor",
        NOW);
  }

  @Test
  void managersAreNormalisedOnceAndKeepWhoAddedThem() {
    Organization created = club(List.of("Ana@Example.org", "ana@example.org ", "bea@example.org"));

    assertThat(created.name()).isEqualTo("Club Demo Baloncesto");
    assertThat(created.managers())
        .extracting(Organization.Manager::emailKey)
        .containsExactly("ana@example.org", "bea@example.org");
    assertThat(created.isManager("ana@example.org")).isTrue();

    Instant later = NOW.plusSeconds(3600);
    Organization rehydrated =
        Organization.rehydrate(
            created.id(),
            created.name(),
            created.contactName(),
            created.email(),
            created.phone(),
            created.managers(),
            null,
            0,
            NOW,
            NOW);
    Organization updated =
        rehydrated.update(
            "Club Demo Baloncesto",
            null,
            "club@example.org",
            null,
            List.of("bea@example.org", "rui@example.org"),
            "sub-outro",
            later);
    assertThat(updated.managers())
        .extracting(Organization.Manager::emailKey, Organization.Manager::addedBy)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("bea@example.org", "sub-xestor"),
            org.assertj.core.groups.Tuple.tuple("rui@example.org", "sub-outro"));
    assertThat(updated.contactName()).isNull();
  }

  @Test
  void atMostTenManagers() {
    List<String> eleven = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      eleven.add("persoa" + i + "@example.org");
    }
    assertThatThrownBy(() -> club(eleven))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.ORGANIZATION_MANAGERS_LIMIT));
    assertThat(club(eleven.subList(0, 10)).managers()).hasSize(10);
  }

  @Test
  void archivedKeepsOnlyTheNameAndIsNotFoundForChanges() {
    Organization archived = club(List.of("ana@example.org")).archive(NOW);

    assertThat(archived.isArchived()).isTrue();
    assertThat(archived.name()).isEqualTo("Club Demo Baloncesto");
    assertThat(archived.email()).isNull();
    assertThat(archived.contactName()).isNull();
    assertThat(archived.phone()).isNull();
    assertThat(archived.managers()).isEmpty();
    assertThatThrownBy(
            () -> archived.update("Outro", null, "club@example.org", null, List.of(), "sub-x", NOW))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.ORGANIZATION_NOT_FOUND));
  }

  @Test
  void theEmailIsRequiredAndChecked() {
    assertThatThrownBy(
            () ->
                Organization.create(
                    UUID.randomUUID(), "Club", null, "sen-arroba", null, List.of(), "sub", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Organization.create(
                    UUID.randomUUID(), "Club", null, "c@example.org", "abc", List.of(), "sub", NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
