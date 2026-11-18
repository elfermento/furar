// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** As reservas dunha entidade e as sesións das tempadas (ADR-0039, punto 5). */
class OrganizationBookingTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-16T18:00:00Z");
  private static final UUID ORG = UUID.randomUUID();
  private static final UUID SERIES = UUID.randomUUID();

  private static BookedSlot slot() {
    return new BookedSlot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        RentalMode.WHOLE,
        START,
        START.plusSeconds(90 * 60),
        2);
  }

  private static Booking session() {
    return Booking.createSession(
        BookingId.newId(),
        new BookingCode("FUR-ABCD"),
        slot(),
        Booking.organizationHolder("Club Demo", Language.GL),
        ORG,
        SERIES,
        "sub-xestor",
        NOW);
  }

  @Test
  void aSessionIsOfTheOrganizationFreeAndCancellableUntilItStarts() {
    Booking s = session();

    assertThat(s.isSession()).isTrue();
    assertThat(s.organizationId()).isEqualTo(ORG);
    assertThat(s.price().kind()).isEqualTo(PriceKind.NONE);
    assertThat(s.paymentStatus()).isEqualTo(PaymentStatus.NONE);
    assertThat(s.cancelUntil()).isEqualTo(START);
    assertThat(s.requireHolder().hasAccount()).isFalse();
    assertThat(s.requireHolder().email()).isNull();
    assertThat(s.canCancel(START.minusSeconds(60))).isTrue();
    assertThat(s.cancelByPerson(START.minusSeconds(60), "sub-resp").status())
        .isEqualTo(BookingStatus.CANCELLED_BY_PERSON);
    assertThatThrownBy(() -> s.cancelByPerson(START, "sub-resp"))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.CANCEL_DEADLINE_PASSED));
  }

  @Test
  void theHolderOfAnOrganizationBookingIsNotEditable() {
    Booking extra =
        Booking.createForOrganization(
            BookingId.newId(),
            new BookingCode("FUR-ABCE"),
            slot(),
            Price.free(),
            BookingRules.none(),
            Booking.organizationHolder("Club Demo", Language.GL),
            ORG,
            "sub-resp",
            NOW);

    assertThat(extra.isSession()).isFalse();
    assertThat(extra.organizationId()).isEqualTo(ORG);
    assertThat(extra.bookedBy()).isEqualTo("sub-resp");
    for (Booking b : new Booking[] {extra, session()}) {
      assertThatThrownBy(
              () ->
                  b.withHolder(Holder.walkIn("Ana Rey", "ana@example.org", null, Language.GL), NOW))
          .isInstanceOfSatisfying(
              DomainException.class,
              e -> assertThat(e.errorCode()).isEqualTo(BookingError.HOLDER_NOT_EDITABLE));
    }
  }

  @Test
  void theInvariantsOfTheTableHoldInTheDomain() {
    Booking free =
        Booking.createFor(
            BookingId.newId(),
            new BookingCode("FUR-ABCF"),
            slot(),
            Price.free(),
            BookingRules.none(),
            Holder.walkIn("Ana Rey", "ana@example.org", null, Language.GL),
            "sub-staff",
            NOW);
    // Unha sesión sen entidade, ou unha reserva da entidade cun correo de persoa, non poden ser.
    assertThatThrownBy(() -> free.restoredFor(null, SERIES))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> free.restoredFor(ORG, null)).isInstanceOf(IllegalStateException.class);
    // Unha sesión con prezo, tampouco.
    Booking organization =
        Booking.createForOrganization(
            BookingId.newId(),
            new BookingCode("FUR-ABCG"),
            slot(),
            Price.paid(500),
            BookingRules.none(),
            Booking.organizationHolder("Club Demo", Language.GL),
            ORG,
            "sub-resp",
            NOW);
    assertThatThrownBy(() -> organization.restoredFor(ORG, SERIES))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anOrganizationBookingIsNeverReviewed() {
    Booking s = session();
    Instant after = START.plusSeconds(3 * 3600);
    assertThat(s.isDone(after)).isTrue();
    assertThat(ReviewRule.reviewable(s, after, null)).isFalse();
  }
}
