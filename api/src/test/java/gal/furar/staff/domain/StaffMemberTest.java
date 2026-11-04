// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import static gal.furar.staff.domain.StaffMembers.EMAIL;
import static gal.furar.staff.domain.StaffMembers.INVITER;
import static gal.furar.staff.domain.StaffMembers.NOW;
import static gal.furar.staff.domain.StaffMembers.SUBJECT;
import static gal.furar.staff.domain.StaffMembers.active;
import static gal.furar.staff.domain.StaffMembers.assertFails;
import static gal.furar.staff.domain.StaffMembers.invited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Language;
import gal.furar.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A pertenza sen Spring (ADR-0027; plan do dominio 4, §1.2.8): cada transición válida e inválida, a
 * regra do último xestor e as invariantes da táboa {@code staff_member}.
 */
class StaffMemberTest {

  static final Instant LATER = NOW.plus(Duration.ofHours(2));

  // --- Invitar ---

  @Test
  void aNewMembershipIsInvitedWithoutPersonNorAcceptance() {
    StaffMember m =
        StaffMember.invite(
            StaffMemberId.newId(),
            EMAIL,
            StaffRole.STAFF,
            Language.ES,
            INVITER,
            NOW.plusNanos(1_234_567));
    assertThat(m.status()).isEqualTo(StaffStatus.INVITED);
    assertThat(m.role()).isEqualTo(StaffRole.STAFF);
    assertThat(m.language()).isEqualTo(Language.ES);
    assertThat(m.email()).isEqualTo(EMAIL);
    assertThat(m.invitedBy()).isEqualTo(INVITER);
    // Microsegundos, como a base.
    assertThat(m.invitedAt()).isEqualTo(NOW.plusNanos(1_234_000));
    assertThat(m.createdAt()).isEqualTo(m.invitedAt()).isEqualTo(m.updatedAt());
    assertThat(m.subject()).isNull();
    assertThat(m.displayName()).isNull();
    assertThat(m.acceptedAt()).isNull();
    assertThat(m.deactivatedAt()).isNull();
    assertThat(m.deactivatedBy()).isNull();
    assertThat(m.lastSignInAt()).isNull();
    assertThat(m.previousSignInAt()).isNull();
    assertThat(m.version()).isNull();
    assertThat(m.activeRole()).isNull();
    assertThat(m.isActiveManager()).isFalse();
  }

  // --- Aceptar: INVITED → ACTIVE ---

  @Test
  void acceptingActivatesWithThePersonAndTheName() {
    StaffMember m =
        invited(StaffRole.MANAGER)
            .accept(" Conserxe@Example.ORG ", SUBJECT, "  Ana Conserxe ", LATER);
    assertThat(m.status()).isEqualTo(StaffStatus.ACTIVE);
    assertThat(m.subject()).isEqualTo(SUBJECT);
    assertThat(m.displayName()).isEqualTo("Ana Conserxe");
    assertThat(m.acceptedAt()).isEqualTo(LATER);
    assertThat(m.updatedAt()).isEqualTo(LATER);
    assertThat(m.activeRole()).isEqualTo(StaffRole.MANAGER);
    assertThat(m.activeRole().role()).isEqualTo(Role.MANAGER);
    assertThat(m.isActiveManager()).isTrue();
  }

  @Test
  void aBlankNameIsNoNameAndALongOneIsCut() {
    assertThat(invited(StaffRole.STAFF).accept(EMAIL.value(), SUBJECT, "   ", NOW).displayName())
        .isNull();
    assertThat(invited(StaffRole.STAFF).accept(EMAIL.value(), SUBJECT, null, NOW).displayName())
        .isNull();
    assertThat(
            invited(StaffRole.STAFF)
                .accept(EMAIL.value(), SUBJECT, "x".repeat(250), NOW)
                .displayName())
        .hasSize(StaffMember.DISPLAY_NAME_MAX);
  }

  @Test
  void acceptingNeedsTheInvitedEmail() {
    StaffMember m = invited(StaffRole.STAFF);
    assertFails(
        StaffError.INVITATION_EMAIL_MISMATCH,
        () -> m.accept("outra@example.org", SUBJECT, null, NOW));
    assertFails(StaffError.INVITATION_EMAIL_MISMATCH, () -> m.accept(null, SUBJECT, null, NOW));
  }

  @Test
  void onlyAnInvitedMembershipIsAccepted() {
    assertFails(
        StaffError.INVITATION_INVALID,
        () -> active(StaffRole.STAFF).accept(EMAIL.value(), "outra", null, NOW));
    StaffMember deactivated = invited(StaffRole.STAFF).deactivate(INVITER, 0, NOW);
    assertFails(
        StaffError.INVITATION_INVALID, () -> deactivated.accept(EMAIL.value(), SUBJECT, null, NOW));
  }

  @Test
  void acceptingWithoutSubjectIsAProgrammingError() {
    assertThatThrownBy(() -> invited(StaffRole.STAFF).accept(EMAIL.value(), " ", null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptanceIsNeverBeforeTheInvitation() {
    StaffMember m =
        invited(StaffRole.STAFF).accept(EMAIL.value(), SUBJECT, null, NOW.minusSeconds(5));
    assertThat(m.acceptedAt()).isEqualTo(m.invitedAt());
  }

  // --- Cambiar o rol ---

  @ParameterizedTest
  @EnumSource(
      value = StaffStatus.class,
      names = {"INVITED", "ACTIVE"})
  void theRoleChangesWhileInvitedOrActive(StaffStatus status) {
    StaffMember m =
        status == StaffStatus.INVITED ? invited(StaffRole.STAFF) : active(StaffRole.STAFF);
    StaffMember changed = m.changeRole(StaffRole.MANAGER, 1, LATER);
    assertThat(changed.role()).isEqualTo(StaffRole.MANAGER);
    assertThat(changed.status()).isEqualTo(status);
    assertThat(changed.updatedAt()).isEqualTo(LATER);
    assertThat(changed.id()).isEqualTo(m.id());
  }

  @Test
  void theSameRoleChangesNothing() {
    StaffMember m = active(StaffRole.MANAGER);
    assertThat(m.changeRole(StaffRole.MANAGER, 1, LATER)).isSameAs(m);
  }

  @Test
  void aDeactivatedMembershipKeepsItsRole() {
    StaffMember deactivated = active(StaffRole.STAFF).deactivate(INVITER, 1, NOW);
    assertFails(
        StaffError.TRANSITION_INVALID, () -> deactivated.changeRole(StaffRole.MANAGER, 1, LATER));
  }

  @Test
  void theLastActiveManagerIsNotDemoted() {
    StaffMember manager = active(StaffRole.MANAGER);
    assertFails(StaffError.LAST_MANAGER, () -> manager.changeRole(StaffRole.STAFF, 1, LATER));
    assertFails(StaffError.LAST_MANAGER, () -> manager.changeRole(StaffRole.STAFF, 0, LATER));
    assertThat(manager.changeRole(StaffRole.STAFF, 2, LATER).role()).isEqualTo(StaffRole.STAFF);
  }

  @Test
  void anInvitedManagerIsNotTheLastManager() {
    // Unha invitada aínda non xestiona: baixala non deixa o concello sen xestor.
    assertThat(invited(StaffRole.MANAGER).changeRole(StaffRole.STAFF, 0, LATER).role())
        .isEqualTo(StaffRole.STAFF);
  }

  // --- Desactivar: INVITED|ACTIVE → DEACTIVATED ---

  @ParameterizedTest
  @EnumSource(
      value = StaffStatus.class,
      names = {"INVITED", "ACTIVE"})
  void deactivatingKeepsTheRowWithWhoAndWhen(StaffStatus status) {
    StaffMember m =
        status == StaffStatus.INVITED ? invited(StaffRole.STAFF) : active(StaffRole.STAFF);
    StaffMember deactivated = m.deactivate("sub-desactiva", 1, LATER);
    assertThat(deactivated.status()).isEqualTo(StaffStatus.DEACTIVATED);
    assertThat(deactivated.deactivatedBy()).isEqualTo("sub-desactiva");
    assertThat(deactivated.deactivatedAt()).isEqualTo(LATER);
    assertThat(deactivated.updatedAt()).isEqualTo(LATER);
    assertThat(deactivated.email()).isEqualTo(m.email());
    assertThat(deactivated.subject()).isEqualTo(m.subject());
    assertThat(deactivated.activeRole()).isNull();
  }

  @Test
  void deactivatingTwiceIsInvalid() {
    StaffMember deactivated = active(StaffRole.STAFF).deactivate(INVITER, 1, NOW);
    assertFails(StaffError.TRANSITION_INVALID, () -> deactivated.deactivate(INVITER, 1, LATER));
  }

  @Test
  void theLastActiveManagerIsNotDeactivated() {
    StaffMember manager = active(StaffRole.MANAGER);
    assertFails(StaffError.LAST_MANAGER, () -> manager.deactivate(INVITER, 1, LATER));
    assertThat(manager.deactivate(INVITER, 2, LATER).status()).isEqualTo(StaffStatus.DEACTIVATED);
    // O persoal (STAFF) e as invitadas non contan.
    assertThat(active(StaffRole.STAFF).deactivate(INVITER, 0, LATER).status())
        .isEqualTo(StaffStatus.DEACTIVATED);
    assertThat(invited(StaffRole.MANAGER).deactivate(INVITER, 0, LATER).status())
        .isEqualTo(StaffStatus.DEACTIVATED);
  }

  @Test
  void deactivatingWithoutWhoIsAProgrammingError() {
    assertThatThrownBy(() -> active(StaffRole.STAFF).deactivate("", 1, LATER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- Reactivar: DEACTIVATED → ACTIVE (ADR-0027, revisión do 2026-10-30) ---

  @Test
  void reactivatingGivesBackTheSameRoleWithoutAnInvitationAndClearsTheDeactivation() {
    StaffMember m = active(StaffRole.MANAGER);
    StaffMember deactivated = m.deactivate("sub-desactiva", 2, NOW);
    StaffMember back = deactivated.reactivate(LATER);
    assertThat(back.status()).isEqualTo(StaffStatus.ACTIVE);
    assertThat(back.role()).isEqualTo(StaffRole.MANAGER);
    assertThat(back.activeRole()).isEqualTo(StaffRole.MANAGER);
    assertThat(back.subject()).isEqualTo(m.subject());
    assertThat(back.email()).isEqualTo(m.email());
    assertThat(back.displayName()).isEqualTo(m.displayName());
    assertThat(back.acceptedAt()).isEqualTo(m.acceptedAt());
    // A conservación conta desde a próxima desactivación, non desde esta.
    assertThat(back.deactivatedAt()).isNull();
    assertThat(back.deactivatedBy()).isNull();
    assertThat(back.updatedAt()).isEqualTo(LATER);
    StaffMember again = back.deactivate("sub-desactiva", 2, LATER.plusSeconds(60));
    assertThat(again.deactivatedAt()).isEqualTo(LATER.plusSeconds(60));
  }

  @Test
  void aBlockedMembershipCanBeReactivatedAndIsNoLongerBlocked() {
    StaffMember deactivated = active(StaffRole.STAFF).deactivate(INVITER, 1, NOW);
    assertThat(deactivated.isIdentityBlocked(LATER)).isTrue();
    StaffMember back = deactivated.reactivate(LATER);
    assertThat(back.isIdentityBlocked(LATER)).isFalse();
  }

  @Test
  void onlyADeactivatedMembershipThatWasAcceptedIsReactivated() {
    assertFails(StaffError.TRANSITION_INVALID, () -> active(StaffRole.STAFF).reactivate(LATER));
    assertFails(StaffError.TRANSITION_INVALID, () -> invited(StaffRole.STAFF).reactivate(LATER));
    // Unha invitada desactivada nunca tivo acceso: invítase de novo.
    StaffMember neverAccepted = invited(StaffRole.STAFF).deactivate(INVITER, 0, NOW);
    assertFails(StaffError.TRANSITION_INVALID, () -> neverAccepted.reactivate(LATER));
  }

  @Test
  void anAnonymisedMembershipIsNotReactivated() {
    StaffMemberId id = StaffMemberId.newId();
    StaffMember anonymised =
        StaffMember.rehydrate(
            id,
            EmailAddress.anonymised(id),
            null,
            SUBJECT,
            StaffRole.STAFF,
            StaffStatus.DEACTIVATED,
            Language.GL,
            INVITER,
            NOW,
            NOW,
            INVITER,
            NOW,
            null,
            null,
            0,
            NOW,
            NOW);
    assertFails(StaffError.ANONYMISED, () -> anonymised.reactivate(LATER));
  }

  // --- Último acceso ---

  @Test
  void onlyALaterAuthTimeIsANewSignIn() {
    StaffMember m = active(StaffRole.STAFF);
    assertThat(m.isNewSignIn(NOW)).isTrue();
    StaffMember signedIn = rehydrate(StaffStatus.ACTIVE, NOW, NOW.minusSeconds(60));
    assertThat(signedIn.isNewSignIn(NOW)).isFalse();
    assertThat(signedIn.isNewSignIn(NOW.minusSeconds(1))).isFalse();
    assertThat(signedIn.isNewSignIn(NOW.plusSeconds(1))).isTrue();
    assertThat(signedIn.lastSignInAt()).isEqualTo(NOW);
    assertThat(signedIn.previousSignInAt()).isEqualTo(NOW.minusSeconds(60));
    assertThat(invited(StaffRole.STAFF).isNewSignIn(NOW)).isFalse();
  }

  // --- Bloqueo da identidade (ADR-0020, revisión do 2026-10-29) ---

  @Test
  void theIdentityIsBlockedOnlyWhenDeactivatedBeforeTheVisibleWindowAndNotYetAnonymised() {
    StaffMember deactivated = active(StaffRole.STAFF).deactivate(INVITER, 1, NOW);

    assertThat(deactivated.isIdentityBlocked(NOW))
        .as("deactivated exactly at the cutoff")
        .isFalse();
    assertThat(deactivated.isIdentityBlocked(NOW.minusSeconds(1)))
        .as("deactivated after")
        .isFalse();
    assertThat(deactivated.isIdentityBlocked(NOW.plusSeconds(1))).as("deactivated before").isTrue();
    assertThat(active(StaffRole.STAFF).isIdentityBlocked(LATER)).as("active").isFalse();
    assertThat(invited(StaffRole.STAFF).isIdentityBlocked(LATER)).as("invited").isFalse();

    StaffMemberId id = StaffMemberId.newId();
    StaffMember anonymised =
        StaffMember.rehydrate(
            id,
            EmailAddress.anonymised(id),
            null,
            SUBJECT,
            StaffRole.STAFF,
            StaffStatus.DEACTIVATED,
            Language.GL,
            INVITER,
            NOW,
            NOW,
            INVITER,
            NOW,
            null,
            null,
            0,
            NOW,
            NOW);
    assertThat(anonymised.isIdentityBlocked(LATER)).as("already anonymised").isFalse();
  }

  // --- Invariantes da táboa ---

  @Test
  void rehydrateKeepsEveryColumn() {
    StaffMember m = rehydrate(StaffStatus.ACTIVE, NOW, null);
    assertThat(m.version()).isEqualTo(3L);
    assertThat(m.displayName()).isEqualTo("Ana");
  }

  @Test
  void stateDataMustMatchTheStatus() {
    // Activa sen aceptación.
    assertThatThrownBy(
            () ->
                StaffMember.rehydrate(
                    StaffMemberId.newId(),
                    EMAIL,
                    null,
                    null,
                    StaffRole.STAFF,
                    StaffStatus.ACTIVE,
                    Language.GL,
                    INVITER,
                    NOW,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    // Invitada con persoa.
    assertThatThrownBy(
            () ->
                StaffMember.rehydrate(
                    StaffMemberId.newId(),
                    EMAIL,
                    null,
                    SUBJECT,
                    StaffRole.STAFF,
                    StaffStatus.INVITED,
                    Language.GL,
                    INVITER,
                    NOW,
                    NOW,
                    null,
                    null,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    // Aceptación a medias: a persoa sen a data.
    assertThatThrownBy(
            () ->
                StaffMember.rehydrate(
                    StaffMemberId.newId(),
                    EMAIL,
                    null,
                    SUBJECT,
                    StaffRole.STAFF,
                    StaffStatus.DEACTIVATED,
                    Language.GL,
                    INVITER,
                    NOW,
                    null,
                    INVITER,
                    NOW,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    // Desactivada sen quen.
    assertThatThrownBy(
            () ->
                StaffMember.rehydrate(
                    StaffMemberId.newId(),
                    EMAIL,
                    null,
                    null,
                    StaffRole.STAFF,
                    StaffStatus.DEACTIVATED,
                    Language.GL,
                    INVITER,
                    NOW,
                    null,
                    null,
                    NOW,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    // Datos de desactivación nunha activa.
    assertThatThrownBy(
            () ->
                StaffMember.rehydrate(
                    StaffMemberId.newId(),
                    EMAIL,
                    null,
                    SUBJECT,
                    StaffRole.STAFF,
                    StaffStatus.ACTIVE,
                    Language.GL,
                    INVITER,
                    NOW,
                    NOW,
                    INVITER,
                    NOW,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    // Acceso anterior sen último, ou posterior a el.
    assertThatThrownBy(() -> rehydrate(StaffStatus.ACTIVE, null, NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> rehydrate(StaffStatus.ACTIVE, NOW, NOW.plusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    // Sen quen invitou.
    assertThatThrownBy(
            () ->
                StaffMember.invite(
                    StaffMemberId.newId(), EMAIL, StaffRole.STAFF, Language.GL, " ", NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  private static StaffMember rehydrate(StaffStatus status, Instant last, Instant previous) {
    return StaffMember.rehydrate(
        StaffMemberId.newId(),
        EMAIL,
        "Ana",
        SUBJECT,
        StaffRole.STAFF,
        status,
        Language.GL,
        INVITER,
        NOW.minus(Duration.ofDays(1)),
        NOW.minus(Duration.ofDays(1)),
        null,
        null,
        last,
        previous,
        3,
        NOW.minus(Duration.ofDays(1)),
        NOW);
  }
}
