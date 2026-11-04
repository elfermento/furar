// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import static gal.furar.staff.domain.StaffMembers.INVITER;
import static gal.furar.staff.domain.StaffMembers.NOW;
import static gal.furar.staff.domain.StaffMembers.assertFails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * O token e a invitación sen Spring (plan do dominio 4, §1.2.6): 32 bytes aleatorios en base64url,
 * o hash SHA-256, a caducidade aos 7 días cun reloxo fixo, un só uso e a substitución.
 */
class InvitationTest {

  static final SecureRandom RANDOM = new SecureRandom();

  // --- Token ---

  @Test
  void aTokenIs32RandomBytesInBase64Url() {
    InvitationToken token = InvitationToken.generate(RANDOM);
    assertThat(token.value()).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
    assertThat(Base64.getUrlDecoder().decode(token.value())).hasSize(InvitationToken.BYTES);
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      assertThat(seen.add(InvitationToken.generate(RANDOM).value())).isTrue();
    }
  }

  @Test
  void theHashIsTheSha256OfTheText() throws Exception {
    InvitationToken token = InvitationToken.generate(RANDOM);
    byte[] expected =
        MessageDigest.getInstance("SHA-256")
            .digest(token.value().getBytes(StandardCharsets.US_ASCII));
    assertThat(token.hash().bytes()).isEqualTo(expected).hasSize(TokenHash.LENGTH);
    assertThat(token.hash())
        .isEqualTo(TokenHash.of(expected))
        .hasSameHashCodeAs(TokenHash.of(expected));
    assertThat(token.hash()).isNotEqualTo(InvitationToken.generate(RANDOM).hash());
  }

  @Test
  void theTokenNeverAppearsInToString() {
    InvitationToken token = InvitationToken.generate(RANDOM);
    assertThat(token.toString())
        .doesNotContain(token.value())
        .isEqualTo("InvitationToken[redacted]");
    String hex = java.util.HexFormat.of().formatHex(token.hash().bytes());
    assertThat(token.hash().toString())
        .doesNotContain(hex)
        .startsWith("TokenHash[" + hex.substring(0, 8));
  }

  @Test
  void parsingAcceptsOnlyTheCanonicalForm() {
    InvitationToken token = InvitationToken.generate(RANDOM);
    assertThat(InvitationToken.parse(token.value())).contains(token);
    assertThat(InvitationToken.parse(token.value()).orElseThrow().hash()).isEqualTo(token.hash());
    assertThat(InvitationToken.parse(null)).isEmpty();
    assertThat(InvitationToken.parse("")).isEmpty();
    assertThat(InvitationToken.parse(token.value() + "=")).isEmpty();
    assertThat(InvitationToken.parse(token.value().substring(1))).isEmpty();
    assertThat(InvitationToken.parse("+" + token.value().substring(1))).isEmpty();
    // Os dous últimos bits sobran: un texto con eles a 1 non é a forma canónica.
    String zeroTail = "A".repeat(42) + "A";
    String oneTail = "A".repeat(42) + "B";
    assertThat(InvitationToken.parse(zeroTail)).isPresent();
    assertThat(InvitationToken.parse(oneTail)).isEmpty();
    assertThat(token).isNotEqualTo(InvitationToken.generate(RANDOM)).isNotEqualTo("x");
    assertThat(token.hashCode())
        .isEqualTo(InvitationToken.parse(token.value()).orElseThrow().hashCode());
  }

  @Test
  void aHashHas32Bytes() {
    assertThatThrownBy(() -> TokenHash.of(new byte[31]))
        .isInstanceOf(IllegalArgumentException.class);
    byte[] bytes = new byte[32];
    TokenHash hash = TokenHash.of(bytes);
    bytes[0] = 1;
    assertThat(hash.bytes()[0]).isZero();
    assertThat(hash).isNotEqualTo("x");
  }

  // --- Invitación ---

  @Test
  void anInvitationExpiresSevenDaysAfterIssued() {
    StaffInvitation i = issue(NOW.plusNanos(999));
    assertThat(i.createdAt()).isEqualTo(NOW);
    assertThat(i.expiresAt()).isEqualTo(Instant.parse("2026-11-12T09:00:00Z"));
    assertThat(i.delivery()).isEqualTo(InvitationDelivery.PENDING);
    assertThat(i.isOpen()).isTrue();
    assertThat(i.isExpired(i.expiresAt().minusNanos(1000))).isFalse();
    assertThat(i.isExpired(i.expiresAt())).isTrue();
    i.requireUsable(i.expiresAt().minusSeconds(1));
    assertFails(StaffError.INVITATION_EXPIRED, () -> i.requireUsable(i.expiresAt()));
    assertThat(i.requestedBy()).isEqualTo(INVITER);
    assertThat(i.usedAt()).isNull();
    assertThat(i.supersededAt()).isNull();
  }

  @Test
  void anInvitationIsUsedOnce() {
    StaffInvitation i = issue(NOW);
    Instant at = NOW.plus(Duration.ofDays(2));
    StaffInvitation used = i.use(at);
    assertThat(used.usedAt()).isEqualTo(at);
    assertThat(used.isOpen()).isFalse();
    assertThat(used.id()).isEqualTo(i.id());
    assertThat(used.memberId()).isEqualTo(i.memberId());
    assertThat(used.tokenHash()).isEqualTo(i.tokenHash());
    assertFails(StaffError.INVITATION_INVALID, () -> used.use(at));
    assertFails(StaffError.INVITATION_INVALID, () -> used.requireUsable(at));
    assertFails(StaffError.INVITATION_EXPIRED, () -> i.use(NOW.plus(Duration.ofDays(7))));
    // Usada, un reenvío xa non a toca.
    assertThat(used.supersede(at)).isSameAs(used);
  }

  @Test
  void aResendOrADeactivationSupersedesIt() {
    StaffInvitation i = issue(NOW);
    StaffInvitation superseded = i.supersede(NOW.plusSeconds(60));
    assertThat(superseded.supersededAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(superseded.isOpen()).isFalse();
    // Substituída: a mesma resposta ca un token descoñecido, aínda que non caducase.
    assertFails(StaffError.INVITATION_INVALID, () -> superseded.requireUsable(NOW.plusSeconds(61)));
    assertThat(superseded.supersede(NOW.plusSeconds(120))).isSameAs(superseded);
    // Nunca antes da emisión.
    assertThat(i.supersede(NOW.minusSeconds(5)).supersededAt()).isEqualTo(NOW);
  }

  @Test
  void theMailResultIsRecorded() {
    StaffInvitation i = issue(NOW);
    assertThat(i.delivered(true).delivery()).isEqualTo(InvitationDelivery.SENT);
    assertThat(i.delivered(false).delivery()).isEqualTo(InvitationDelivery.FAILED);
    assertThat(i.delivered(true).isOpen()).isTrue();
  }

  @Test
  void invariantsOfTheTable() {
    StaffInvitation i = issue(NOW);
    // Outra caducidade.
    assertThatThrownBy(
            () ->
                StaffInvitation.rehydrate(
                    i.id(),
                    i.memberId(),
                    i.tokenHash(),
                    INVITER,
                    NOW,
                    NOW.plus(Duration.ofDays(8)),
                    null,
                    null,
                    InvitationDelivery.SENT))
        .isInstanceOf(IllegalStateException.class);
    // Usada e substituída.
    assertThatThrownBy(
            () ->
                StaffInvitation.rehydrate(
                    i.id(),
                    i.memberId(),
                    i.tokenHash(),
                    INVITER,
                    NOW,
                    i.expiresAt(),
                    NOW,
                    NOW,
                    InvitationDelivery.SENT))
        .isInstanceOf(IllegalStateException.class);
    // Usada despois de caducar ou antes de emitirse.
    assertThatThrownBy(
            () ->
                StaffInvitation.rehydrate(
                    i.id(),
                    i.memberId(),
                    i.tokenHash(),
                    INVITER,
                    NOW,
                    i.expiresAt(),
                    i.expiresAt(),
                    null,
                    InvitationDelivery.SENT))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                StaffInvitation.rehydrate(
                    i.id(),
                    i.memberId(),
                    i.tokenHash(),
                    INVITER,
                    NOW,
                    i.expiresAt(),
                    NOW.minusSeconds(1),
                    null,
                    InvitationDelivery.SENT))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                StaffInvitation.issue(
                    StaffInvitationId.newId(), i.memberId(), i.tokenHash(), "", NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            StaffInvitation.rehydrate(
                    i.id(),
                    i.memberId(),
                    i.tokenHash(),
                    INVITER,
                    NOW,
                    i.expiresAt(),
                    null,
                    null,
                    InvitationDelivery.FAILED)
                .delivery())
        .isEqualTo(InvitationDelivery.FAILED);
  }

  static StaffInvitation issue(Instant now) {
    return StaffInvitation.issue(
        StaffInvitationId.newId(),
        StaffMemberId.newId(),
        InvitationToken.generate(RANDOM).hash(),
        INVITER,
        now);
  }
}
