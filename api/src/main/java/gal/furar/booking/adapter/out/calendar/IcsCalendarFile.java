// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.calendar;

import gal.furar.booking.application.port.out.CalendarFile;
import gal.furar.booking.domain.Booking;
import gal.furar.shared.domain.Language;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * O {@code .ics} dunha reserva escrito á man (RFC 5545; ADR-0025, punto 14): un {@code VEVENT} con
 * {@code UID} = id da reserva e {@code SEQUENCE} = versión, así un cambio substitúe o evento no
 * calendario; horas en UTC; {@code METHOD:CANCEL} se está cancelada. Escapa os textos (§3.3.11) e
 * prega as liñas a 75 octetos sen partir un carácter (§3.1), con {@code CRLF}.
 */
@Component
class IcsCalendarFile implements CalendarFile {

  static final String CRLF = "\r\n";
  private static final int MAX_OCTETS = 75;
  private static final DateTimeFormatter UTC =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  @Override
  public String of(
      Booking booking,
      String facilityName,
      @Nullable String address,
      String useName,
      @Nullable String partName) {
    boolean cancelled = booking.status().isCancelled();
    String what = partName == null ? useName : useName + " (" + partName + ")";
    StringBuilder ics = new StringBuilder();
    line(ics, "BEGIN:VCALENDAR");
    line(ics, "VERSION:2.0");
    line(
        ics,
        "PRODID:-//Furar//Reservas//"
            + booking.requireHolder().language().code().toUpperCase(Locale.ROOT));
    line(ics, "CALSCALE:GREGORIAN");
    line(ics, "METHOD:" + (cancelled ? "CANCEL" : "PUBLISH"));
    line(ics, "BEGIN:VEVENT");
    line(ics, "UID:" + booking.id().value());
    line(ics, "DTSTAMP:" + utc(booking.updatedAt()));
    line(ics, "SEQUENCE:" + (booking.version() == null ? 0 : booking.version()));
    line(ics, "DTSTART:" + utc(booking.slot().start()));
    line(ics, "DTEND:" + utc(booking.slot().end()));
    line(ics, "SUMMARY:" + text(what + " · " + facilityName));
    line(ics, "LOCATION:" + text(address == null ? facilityName : facilityName + ", " + address));
    line(
        ics,
        "DESCRIPTION:" + text(description(booking.requireHolder().language()) + booking.code()));
    line(ics, "STATUS:" + (cancelled ? "CANCELLED" : "CONFIRMED"));
    line(ics, "END:VEVENT");
    line(ics, "END:VCALENDAR");
    return ics.toString();
  }

  /** O texto antes do código, no idioma da reserva. */
  private static String description(Language language) {
    return switch (language) {
      case GL -> "Código da reserva: ";
      case ES -> "Código de la reserva: ";
    };
  }

  private static String utc(Instant at) {
    return UTC.format(at);
  }

  /** TEXT (RFC 5545, §3.3.11): barra invertida, punto e coma, coma e saltos de liña. */
  static String text(String value) {
    return value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n");
  }

  /**
   * Unha liña de contido pregada (RFC 5545, §3.1): ata 75 octetos UTF-8 por liña física; as
   * seguintes empezan cun espazo, que tamén conta.
   */
  static void line(StringBuilder out, String content) {
    int octets = 0;
    int i = 0;
    while (i < content.length()) {
      int codePoint = content.codePointAt(i);
      int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
      if (octets + size > MAX_OCTETS) {
        out.append(CRLF).append(' ');
        octets = 1;
      }
      out.appendCodePoint(codePoint);
      octets += size;
      i += Character.charCount(codePoint);
    }
    out.append(CRLF);
  }
}
