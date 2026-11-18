// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Language;
import gal.furar.staff.application.port.in.ActivityEntry.ActorKind;
import gal.furar.staff.application.port.in.StaffMemberView.Inviter;
import gal.furar.staff.domain.EmailAddress;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffRole;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Quen invitou: persoal do concello ou a plataforma, co criterio do rexistro de actividade. */
class InviterTest {

  private static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("xestora@example.org");

  @Test
  void withoutMembershipInTheMunicipalityItIsThePlatformWithoutAnyAccountData() {
    assertThat(Inviter.of(List.of(), NOW)).isEqualTo(new Inviter(ActorKind.PLATFORM, null));
  }

  @Test
  void withAMembershipItIsStaffWithTheNewestName() {
    assertThat(Inviter.of(List.of(member(null), member("Xestora Narón"), member("Antiga")), NOW))
        .isEqualTo(new Inviter(ActorKind.MEMBER, "Xestora Narón"));
  }

  @Test
  void anAnonymisedOrNamelessMembershipIsStillStaffWithoutName() {
    assertThat(Inviter.of(List.of(member(null)), NOW))
        .isEqualTo(new Inviter(ActorKind.MEMBER, null));
  }

  @Test
  void aBlockedLeaverIsStillStaffButWithoutName() {
    StaffMember deactivated = member("Xestora Narón").deactivate("sub-plataforma", 2, NOW);
    // A desactivación xa non se ve no rexistro: nome bloqueado.
    assertThat(Inviter.of(List.of(deactivated), NOW.plusSeconds(1)))
        .isEqualTo(new Inviter(ActorKind.MEMBER, null));
    // Aínda visible: co seu nome.
    assertThat(Inviter.of(List.of(deactivated), NOW.minusSeconds(1)))
        .isEqualTo(new Inviter(ActorKind.MEMBER, "Xestora Narón"));
  }

  @Test
  void onlyMembersAndThePlatformInviteAndThePlatformHasNoName() {
    assertThatThrownBy(() -> new Inviter(ActorKind.SYSTEM, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inviter(ActorKind.OTHER, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inviter(ActorKind.PLATFORM, "Admin"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static StaffMember member(@Nullable String name) {
    return StaffMember.invite(
            StaffMemberId.newId(), EMAIL, StaffRole.MANAGER, Language.GL, "sub-plataforma", NOW)
        .accept(EMAIL.value(), "sub-xestora", name, NOW);
  }
}
