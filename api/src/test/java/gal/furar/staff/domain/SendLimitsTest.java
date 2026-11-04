// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import static gal.furar.staff.domain.StaffMembers.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Límites de envío (plan do dominio 4, §1.2.7) cun reloxo fixo: 30 por concello en 24 h, 2 min
 * entre envíos á mesma pertenza e 5 por pertenza, co tempo para {@code Retry-After}.
 */
class SendLimitsTest {

  @Test
  void aFirstInvitationIsAllowed() {
    assertThatCode(() -> SendLimits.check(List.of(), List.of(), NOW)).doesNotThrowAnyException();
  }

  @Test
  void theThirtyFirstInADayWaitsUntilTheThirtiethLeavesTheWindow() {
    // Un envío cada minuto desde hai 29 min: 30 na xanela.
    List<Instant> sends = everyMinute(30);
    InvitationRateLimitedException e = limited(sends, List.of());
    // O 30.º máis novo foi hai 29 min: sae da xanela en 24 h − 29 min.
    assertThat(e.retryAfterSeconds()).contains(Duration.ofHours(24).minusMinutes(29).toSeconds());
    assertThat(e.errorCode()).isEqualTo(StaffError.INVITATION_RATE_LIMITED);
    assertThat(e.errorCode().status()).isEqualTo(429);
    // Con 29, aínda cabe.
    assertThatCode(() -> SendLimits.check(sends.subList(0, 29), List.of(), NOW))
        .doesNotThrowAnyException();
  }

  @Test
  void sendsOlderThanADayDoNotCount() {
    List<Instant> sends = new ArrayList<>(everyMinute(29));
    sends.add(NOW.minus(Duration.ofHours(24)));
    sends.add(NOW.minus(Duration.ofDays(3)));
    assertThatCode(() -> SendLimits.check(sends, List.of(), NOW)).doesNotThrowAnyException();
  }

  @Test
  void twoMinutesBetweenTwoSendsToTheSameMembership() {
    assertThat(SendLimits.MEMBER_GAP).isEqualTo(Duration.ofMinutes(2));
    List<Instant> member = List.of(NOW.minus(Duration.ofSeconds(15)).minusMillis(500));
    assertThat(limited(List.of(), member).retryAfterSeconds()).contains(105L);
    assertThatCode(
            () -> SendLimits.check(List.of(), List.of(NOW.minus(SendLimits.MEMBER_GAP)), NOW))
        .doesNotThrowAnyException();
    // Sempre polo menos 1 s.
    assertThat(
            limited(List.of(), List.of(NOW.minus(SendLimits.MEMBER_GAP).plusNanos(1)))
                .retryAfterSeconds())
        .contains(1L);
  }

  @Test
  void theLongestWaitWins() {
    List<Instant> member = List.of(NOW.minus(Duration.ofMinutes(1)));
    InvitationRateLimitedException e = limited(everyMinute(30), member);
    assertThat(e.retryAfterSeconds()).contains(Duration.ofHours(24).minusMinutes(29).toSeconds());
  }

  @Test
  void fiveSendsPerMembershipAndWaitingDoesNotHelp() {
    List<Instant> member = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      member.add(NOW.minus(Duration.ofHours(i + 1)));
    }
    assertThatCode(() -> SendLimits.check(List.of(), member, NOW)).doesNotThrowAnyException();
    member.add(NOW.minus(Duration.ofDays(6)));
    assertThat(limited(List.of(), member).retryAfterSeconds()).isEmpty();
  }

  @Test
  void theResendAvailabilityFollowsTheSameMemberLimits() {
    Instant last = NOW.minus(Duration.ofSeconds(15));
    // Dentro dos 2 min: desde cando.
    assertThat(SendLimits.resendAvailability(1, last, NOW))
        .isEqualTo(new ResendAvailability(last.plus(SendLimits.MEMBER_GAP), false));
    // Xusto aos 2 min, ou sen envíos: xa se pode.
    assertThat(SendLimits.resendAvailability(1, NOW.minus(SendLimits.MEMBER_GAP), NOW))
        .isEqualTo(new ResendAvailability(null, false));
    assertThat(SendLimits.resendAvailability(0, null, NOW))
        .isEqualTo(new ResendAvailability(null, false));
    // Gastados os 5, aínda que o último sexa recente: non hai espera que valla.
    assertThat(SendLimits.resendAvailability(SendLimits.PER_MEMBER, last, NOW))
        .isEqualTo(new ResendAvailability(null, true));
    assertThat(SendLimits.resendAvailability(SendLimits.PER_MEMBER - 1, last, NOW).exhausted())
        .isFalse();
  }

  private static List<Instant> everyMinute(int count) {
    List<Instant> sends = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      sends.add(NOW.minus(Duration.ofMinutes(i)));
    }
    return sends;
  }

  private static InvitationRateLimitedException limited(
      List<Instant> municipality, List<Instant> member) {
    InvitationRateLimitedException e =
        catchThrowableOfType(
            InvitationRateLimitedException.class,
            () -> SendLimits.check(municipality, member, NOW));
    assertThat(e).isNotNull();
    return e;
  }
}
