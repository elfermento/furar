// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.Price;
import gal.furar.booking.domain.RentalMode;
import gal.furar.municipality.MunicipalityOpenMatchPolicy;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unha reserva dunha entidade non se abre a outras persoas (ADR-0039, punto 5; ADR-0040): a súa
 * partida non sería de ninguén.
 */
class OpenMatchOrganizationTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-20T18:00:00Z");
  private static final MunicipalityOpenMatchPolicy POLICY =
      new MunicipalityOpenMatchPolicy(NOW.minusSeconds(30 * 86400L), 12, null);

  private static BookedSlot slot() {
    return new BookedSlot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        RentalMode.WHOLE,
        START,
        START.plusSeconds(3600),
        1);
  }

  @Test
  void anOrganizationBookingIsNotOpenable() {
    Booking extra =
        Booking.createForOrganization(
            BookingId.newId(),
            new BookingCode("FUR-ABCD"),
            slot(),
            Price.free(),
            BookingRules.none(),
            Booking.organizationHolder("Club Demo", Language.GL),
            UUID.randomUUID(),
            "sub-resp",
            NOW);

    assertThatThrownBy(() -> OpenMatchService.checkOpenable(extra, POLICY, NOW))
        .isInstanceOfSatisfying(
            DomainException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.errorCode())
                    .isEqualTo(BookingError.MATCH_NOT_ALLOWED));
  }

  @Test
  void aPersonBookingStillIs() {
    Booking own =
        Booking.create(
            BookingId.newId(),
            new BookingCode("FUR-ABCE"),
            slot(),
            Price.free(),
            BookingRules.none(),
            gal.furar.booking.domain.Holder.account(
                "sub-ana", "Ana Rey", "ana@example.org", Language.GL),
            NOW);

    assertThatCode(() -> OpenMatchService.checkOpenable(own, POLICY, NOW))
        .doesNotThrowAnyException();
  }
}
