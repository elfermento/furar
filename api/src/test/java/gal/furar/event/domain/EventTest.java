// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Invariantes do evento (ADR-0038). */
class EventTest {

  static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
  static final Instant STARTS = NOW.plus(Duration.ofDays(10));

  static EventDetails details(Integer capacity, Integer price, Instant deadline, boolean approval) {
    return new EventDetails(
        EventText.name("Carreira popular", "Carrera popular"),
        null,
        STARTS,
        EventPlace.facility(UUID.randomUUID()),
        capacity,
        price,
        deadline,
        true,
        approval);
  }

  static Event event(EventDetails details) {
    return Event.rehydrate(EventId.newId(), details, null, null, 0, NOW, NOW);
  }

  static String code(Runnable action) {
    try {
      action.run();
    } catch (DomainException e) {
      return e.errorCode().code();
    }
    throw new AssertionError("no error");
  }

  @Test
  void theNameGoesInBothLanguagesAndTheDescriptionToo() {
    assertThat(code(() -> EventText.name("Carreira", " "))).isEqualTo("event.name-invalid");
    assertThat(code(() -> EventText.name("x".repeat(101), "y"))).isEqualTo("event.name-invalid");
    assertThat(EventText.name(" Carreira ", "Carrera").gl()).isEqualTo("Carreira");
    assertThat(EventText.description(" ", null)).isNull();
    assertThat(code(() -> EventText.description("Texto", null)))
        .isEqualTo("event.description-invalid");
  }

  @Test
  void thePlaceIsAFacilityOrAnotherPlaceNeverBoth() {
    assertThat(code(() -> new EventPlace(null, " "))).isEqualTo("event.place-invalid");
    assertThat(code(() -> new EventPlace(UUID.randomUUID(), "Praza")))
        .isEqualTo("event.place-invalid");
    assertThat(EventPlace.other(" Saída da praza ").other()).isEqualTo("Saída da praza");
  }

  @Test
  void seatsPriceAndDeadlineHaveLimits() {
    assertThat(code(() -> details(0, null, null, false))).isEqualTo("event.capacity-invalid");
    assertThat(code(() -> details(null, 0, null, false))).isEqualTo("event.price-invalid");
    assertThat(code(() -> details(null, null, STARTS.plusSeconds(60), false)))
        .isEqualTo("event.deadline-invalid");
    assertThat(details(null, null, null, false).closesAt()).isEqualTo(STARTS);
  }

  @Test
  void itTakesRegistrationsUntilTheDeadlineOrTheStart() {
    Instant deadline = STARTS.minus(Duration.ofDays(2));
    Event event = event(details(10, null, deadline, false));
    assertThat(event.open(NOW)).isTrue();
    assertThat(event.open(deadline)).isFalse();
    assertThat(code(() -> event.checkOpen(deadline))).isEqualTo("event.closed");
    Event draft =
        event(
            new EventDetails(
                EventText.name("A", "A"),
                null,
                STARTS,
                EventPlace.other("Praza"),
                null,
                null,
                null,
                false,
                false));
    assertThat(draft.open(NOW)).isFalse();
    assertThat(draft.visible()).isFalse();
  }

  @Test
  void aCancelledOrStartedEventDoesNotChange() {
    Event event = event(details(null, null, null, false));
    Event cancelled = event.cancel(NOW);
    assertThat(cancelled.visible()).isFalse();
    assertThat(code(() -> cancelled.checkOpen(NOW))).isEqualTo("event.cancelled");
    assertThat(code(() -> cancelled.cancel(NOW))).isEqualTo("event.cancelled");
    assertThat(code(() -> event.update(event.details(), STARTS))).isEqualTo("event.started");
  }

  @Test
  void onlyDateTimeOrPlaceCountAsAMove() {
    EventDetails before = details(10, null, null, false);
    EventDetails seats =
        new EventDetails(before.name(), null, STARTS, before.place(), 20, 500, null, true, true);
    assertThat(seats.movedFrom(before)).isFalse();
    EventDetails later =
        new EventDetails(
            before.name(),
            null,
            STARTS.plusSeconds(3600),
            before.place(),
            10,
            null,
            null,
            true,
            false);
    assertThat(later.movedFrom(before)).isTrue();
  }

  @Test
  void aRegistrationIsPendingWithApprovalAndPaysWithAPrice() {
    Event paid = event(details(10, 500, null, true));
    Registration r =
        Registration.open(
            paid, new Participant("sub", "Persoa", "p@example.org", Language.GL), NOW);
    assertThat(r.status()).isEqualTo(RegistrationStatus.PENDING);
    assertThat(r.payment()).isEqualTo(PaymentStatus.PENDING);
    assertThat(code(() -> r.pay(PaymentStatus.PAID, paid, NOW))).isEqualTo("event.no-payment");
    Registration accepted = r.accept(NOW);
    assertThat(accepted.confirmed()).isTrue();
    assertThat(code(() -> accepted.reject(NOW))).isEqualTo("event.not-pending");
    assertThat(accepted.pay(PaymentStatus.PAID, paid, NOW).payment()).isEqualTo(PaymentStatus.PAID);

    Event free = event(details(null, null, null, false));
    Registration direct =
        Registration.open(free, new Participant("s", null, null, Language.ES), NOW);
    assertThat(direct.confirmed()).isTrue();
    assertThat(direct.paymentFor(free)).isNull();
    assertThat(code(() -> direct.pay(PaymentStatus.PAID, free, NOW))).isEqualTo("event.no-payment");
    assertThat(direct.paymentFor(paid)).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void theParticipantHidesPersonalDataInLogs() {
    assertThat(new Participant("sub", "Persoa", "p@example.org", Language.GL).toString())
        .doesNotContain("Persoa")
        .doesNotContain("example");
    assertThatThrownBy(() -> new Participant(null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
