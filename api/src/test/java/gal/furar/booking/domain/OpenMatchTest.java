// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** As contas, as regras de abrir e editar e o prazo dunha partida aberta (ADR-0040). */
class OpenMatchTest {

  static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  static OpenMatch.Terms terms(int known, int minimum, int maximum) {
    return new OpenMatch.Terms(known, minimum, maximum, null, "  ", "  Ven xogar  ", false);
  }

  static OpenMatch open(int known, int minimum, int maximum) {
    return OpenMatch.open(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "sub-ana",
        "Ana Rey",
        terms(known, minimum, maximum),
        NOW);
  }

  static String code(Throwable e) {
    return ((DomainException) e).errorCode().code();
  }

  @Test
  void countsTotalMissingAndLeftWithTheConfirmedOnly() {
    OpenMatch m = open(3, 4, 10);

    assertThat(m.total(2)).isEqualTo(5);
    assertThat(m.missing(0)).isEqualTo(1);
    assertThat(m.missing(2)).isZero();
    assertThat(m.left(2)).isEqualTo(5);
    assertThat(m.full(7)).isTrue();
    assertThat(m.filled(1)).isTrue();
    assertThat(m.filled(0)).isFalse();
  }

  @Test
  void aMatchCanLackASinglePerson() {
    OpenMatch m = open(3, 4, 4);

    assertThat(m.missing(0)).isEqualTo(1);
    assertThat(m.left(0)).isEqualTo(1);
  }

  @Test
  void blankTextsAreNoTextAndTextsAreStripped() {
    OpenMatch m = open(1, 2, 4);

    assertThat(m.title()).isNull();
    assertThat(m.description()).isEqualTo("Ven xogar");
    assertThat(m.status()).isEqualTo(OpenMatchStatus.OPEN);
  }

  @Test
  void impossibleCountsAreRejected() {
    for (int[] c : new int[][] {{1, 1, 4}, {1, 5, 4}, {4, 2, 4}, {0, 2, 4}, {1, 2, 41}}) {
      assertThatThrownBy(() -> terms(c[0], c[1], c[2]))
          .isInstanceOf(DomainException.class)
          .extracting(OpenMatchTest::code)
          .isEqualTo("booking.match-counts-invalid");
    }
  }

  @Test
  void neverPublishesAnEmailAddressAsTheName() {
    for (String name : new String[] {null, " ", "ana@example.org"}) {
      assertThatThrownBy(() -> OpenMatch.publicName(name))
          .extracting(OpenMatchTest::code)
          .isEqualTo("booking.match-name-required");
    }
    assertThat(OpenMatch.publicName("  Ana Rey ")).isEqualTo("Ana Rey");
  }

  @Test
  void opensOnlyWhileTheBookingCancelsForFreeAndBeforeTheDeadline() {
    Instant start = NOW.plus(Duration.ofHours(20));
    // A reserva cancélase de balde ata 24 horas antes: xa pasou.
    assertThatThrownBy(
            () -> OpenMatch.checkInTime(start.minus(Duration.ofHours(24)), start, 12, NOW))
        .extracting(OpenMatchTest::code)
        .isEqualTo("booking.match-too-late");
    // O prazo do concello (12 horas antes) aínda non pasou e a reserva aínda se cancela de balde.
    OpenMatch.checkInTime(start.minus(Duration.ofHours(1)), start, 12, NOW);
    // O prazo do concello (24 horas antes) xa pasou.
    assertThatThrownBy(() -> OpenMatch.checkInTime(start, start, 24, NOW))
        .isInstanceOf(DomainException.class)
        .satisfies(
            e ->
                assertThat(((DomainException) e).params())
                    .containsEntry("deadline", start.minus(Duration.ofHours(24)).toString()));
  }

  @Test
  void editingCannotLeaveTheMaximumBelowWhoAlreadyGoes() {
    OpenMatch m = open(2, 4, 10);

    assertThatThrownBy(() -> m.edit(terms(2, 4, 5), "Ana Rey", 4, NOW))
        .extracting(OpenMatchTest::code)
        .isEqualTo("booking.match-maximum-below-count");
    OpenMatch edited = m.close(NOW).edit(terms(2, 4, 6), "Ana Rey", 4, NOW);
    assertThat(edited.maximum()).isEqualTo(6);
    assertThat(edited.status()).isEqualTo(OpenMatchStatus.OPEN);
  }

  @Test
  void aCancelledMatchIsNeverReopened() {
    OpenMatch cancelled = open(2, 4, 10).cancel(OpenMatchCancelReason.UNFILLED, NOW);

    assertThat(cancelled.cancelReason()).isEqualTo(OpenMatchCancelReason.UNFILLED);
    assertThatThrownBy(() -> cancelled.reopen(0, NOW))
        .extracting(OpenMatchTest::code)
        .isEqualTo("booking.match-closed");
    assertThatThrownBy(() -> cancelled.edit(terms(2, 4, 10), "Ana Rey", 0, NOW))
        .extracting(OpenMatchTest::code)
        .isEqualTo("booking.match-closed");
  }

  @Test
  void hidingTwiceChangesNothing() {
    OpenMatch hidden = open(2, 4, 10).hidden(true, "sub-xestor", NOW);

    assertThat(hidden.hidden()).isTrue();
    assertThat(hidden.hidden(true, "outro", NOW.plusSeconds(5))).isSameAs(hidden);
    assertThat(hidden.hidden(false, "sub-xestor", NOW).hidden()).isFalse();
  }

  @Test
  void theDeadlineIsTheStartMinusTheHours() {
    Instant start = Instant.parse("2026-11-14T10:00:00Z");

    assertThat(OpenMatch.deadline(start, 12)).isEqualTo(Instant.parse("2026-11-13T22:00:00Z"));
  }

  @Test
  void whoJoinsIsInsideWithoutApprovalAndPendingWithIt() {
    OpenMatchPlayer in =
        OpenMatchPlayer.join(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "s",
            "x@example.org",
            "Xan",
            Language.GL,
            false,
            NOW);
    OpenMatchPlayer asks =
        OpenMatchPlayer.join(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "s",
            "x@example.org",
            "Xan",
            Language.GL,
            true,
            NOW);

    assertThat(in.status()).isEqualTo(OpenMatchPlayerStatus.CONFIRMED);
    assertThat(asks.status()).isEqualTo(OpenMatchPlayerStatus.PENDING);
    assertThat(asks.decidedAt()).isNull();
    assertThat(in.toString()).doesNotContain("x@example.org").doesNotContain("Xan");
  }
}
