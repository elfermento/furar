// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.booking.domain.Price;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR-0025, punto 14: o {@code .ics} á man é igual ao ficheiro esperado, byte a byte ({@code CRLF},
 * escapado, pregado a 75 octetos sen partir un carácter), tamén a noite do cambio de hora e
 * cancelada.
 */
class IcsCalendarFileTest {

  static final UUID ID = UUID.fromString("019a0b6e-0000-7000-8000-0000000000c1");
  static final UUID FACILITY = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f1");
  static final UUID USE = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a1");
  static final UUID PART = UUID.fromString("019a0b6e-0000-7000-8000-0000000000b1");

  final IcsCalendarFile calendar = new IcsCalendarFile();

  /**
   * A noite do 25 de outubro de 2026: dúas franxas de 60 minutos desde as 2:00 de verán (0:00 UTC)
   * ata as 3:00 de inverno (2:00 UTC). En UTC non hai ambigüidade. O lugar leva o enderezo.
   */
  @Test
  void aConfirmedBookingTheNightTheClocksGoBack() throws IOException {
    Booking booking =
        booking(
            Instant.parse("2026-10-25T00:00:00Z"),
            Instant.parse("2026-10-25T02:00:00Z"),
            2,
            RentalMode.WHOLE,
            BookingStatus.CONFIRMED,
            Language.GL,
            3);

    String ics =
        calendar.of(
            booking,
            "Pavillón Municipal do Alto",
            "Rúa do Alto, 12, 15570 Narón",
            "Pista enteira",
            null);

    assertThat(ics).isEqualTo(expected("confirmada-cambio-de-hora.ics"));
  }

  /**
   * En castelán, cancelada, sen enderezo, cunha parte e textos con comas, punto e coma e acentos
   * longos.
   */
  @Test
  void aCancelledBookingFoldsAndEscapes() throws IOException {
    Booking booking =
        booking(
            Instant.parse("2026-11-10T17:00:00Z"),
            Instant.parse("2026-11-10T18:30:00Z"),
            1,
            RentalMode.PARTS,
            BookingStatus.CANCELLED_BY_PERSON,
            Language.ES,
            1);

    String ics =
        calendar.of(
            booking,
            "Campo de fútbol de herba artificial de Piñeiros; vestiarios, bancadas e iluminación",
            null,
            "Fútbol 7",
            "Metade norte, a carón da rúa");

    assertThat(ics).isEqualTo(expected("cancelada-pregada.ics"));
    for (String line : ics.split("\\r\\n")) {
      assertThat(line.getBytes(StandardCharsets.UTF_8).length)
          .as("liña física: %s", line)
          .isLessThanOrEqualTo(75);
    }
  }

  @Test
  void escapesTheTextCharacters() {
    assertThat(IcsCalendarFile.text("a\\b;c,d\ne")).isEqualTo("a\\\\b\\;c\\,d\\ne");
  }

  static Booking booking(
      Instant start,
      Instant end,
      int slots,
      RentalMode mode,
      BookingStatus status,
      Language language,
      long version) {
    boolean cancelled = status.isCancelled();
    return Booking.rehydrate(
        new BookingId(ID),
        new BookingCode("FUR-AB23"),
        new BookedSlot(
            FACILITY, USE, mode == RentalMode.PARTS ? PART : null, mode, start, end, slots),
        status,
        PaymentStatus.NONE,
        Price.free(),
        BookingRules.none(),
        start,
        new Holder("sub-1", "Ana Rey", "ana@example.org", language),
        cancelled ? Instant.parse("2026-10-20T08:00:00Z") : null,
        cancelled ? "sub-1" : null,
        version,
        Instant.parse("2026-10-20T07:00:00Z"),
        Instant.parse("2026-10-20T08:00:00Z"));
  }

  static String expected(String name) throws IOException {
    try (InputStream in =
        IcsCalendarFileTest.class.getResourceAsStream("/booking/calendar/" + name)) {
      assertThat(in).as(name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
