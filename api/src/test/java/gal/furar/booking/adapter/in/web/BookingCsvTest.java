// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.BookingFieldColumn;
import gal.furar.booking.application.port.in.ManagedBooking;
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
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * O CSV das reservas sen Spring (maqueta 60): BOM, «;», CRLF, coma decimal, horas de Europe/Madrid,
 * cabeceiras e valores no idioma de quen descarga, os campos propios coas súas etiquetas e as
 * fórmulas neutralizadas.
 */
class BookingCsvTest {

  static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

  static final List<BookingFieldColumn> FIELDS =
      List.of(
          new BookingFieldColumn(
              "level",
              new LocalizedText("Nivel", "Nivel"),
              Map.of("mid", new LocalizedText("Medio", "Medio"))),
          new BookingFieldColumn("federated", new LocalizedText("Federada", "Federada"), Map.of()),
          new BookingFieldColumn("weight", new LocalizedText("Peso; kg", "Peso; kg"), Map.of()));

  @Test
  void headerRowsAndCustomFieldsInGalician() {
    String csv =
        csv(
            Language.GL,
            view(
                booking(
                    "2026-11-05T18:00:00Z",
                    "2026-11-05T19:00:00Z",
                    BookingStatus.CONFIRMED,
                    PaymentStatus.PENDING,
                    1200,
                    new Holder("s", "Antía Vázquez", "antia@example.org", Language.GL)),
                "Pista de Piñeiros",
                "Baloncesto",
                "pista central",
                Map.of(
                    "level",
                    new CustomFieldValue.Choice("mid"),
                    "federated",
                    new CustomFieldValue.Bool(true),
                    "weight",
                    new CustomFieldValue.Number(new BigDecimal("70.5")))));

    assertThat(csv).startsWith(BookingCsv.BOM);
    assertThat(csv.substring(1).split("\r\n", -1))
        .containsExactly(
            "Data;Inicio;Fin;Instalación;Uso;Persoa;Correo;Teléfono;Estado;Pago;Importe (€);"
                + "Código;Reservada o;Reservada por;Nivel;Federada;\"Peso; kg\"",
            "05/11/2026;19:00;20:00;Pista de Piñeiros;Baloncesto · pista central;Antía Vázquez;"
                + "antia@example.org;;Confirmada;Pendente;12,00;FUR-AB23;03/11/2026 10:00;;Medio;Si;"
                + "70,5",
            "");
  }

  @Test
  void spanishTextsDoneCancelledAnonymisedAndFree() {
    String csv =
        csv(
            Language.ES,
            view(
                booking(
                    "2026-11-01T09:00:00Z",
                    "2026-11-01T10:00:00Z",
                    BookingStatus.CONFIRMED,
                    PaymentStatus.NONE,
                    null,
                    null),
                "Piscina",
                "Nado libre",
                null,
                Map.of()),
            view(
                booking(
                    "2026-11-06T09:00:00Z",
                    "2026-11-06T10:00:00Z",
                    BookingStatus.CANCELLED_BY_MUNICIPALITY,
                    PaymentStatus.TO_REFUND,
                    800,
                    new Holder("s", "Brais", "brais@example.org", Language.GL)),
                "Piscina",
                "Nado libre",
                null,
                Map.of()));

    String[] lines = csv.substring(1).split("\r\n");
    assertThat(lines[0])
        .startsWith("Fecha;Inicio;Fin;Instalación;Uso;Persona;Correo;Teléfono;")
        .contains(";Reservada el;Reservada por;");
    assertThat(lines[1])
        .isEqualTo(
            "01/11/2026;10:00;11:00;Piscina;Nado libre;Persona anonimizada;;;Realizada;Gratis;;"
                + "FUR-AB23;03/11/2026 10:00;;;;");
    assertThat(lines[2]).contains(";Cancelada por el ayuntamiento;A devolver;8,00;");
  }

  @Test
  void bookingsMadeByTheStaffCarryThePhoneAndWhoMadeThem() {
    Booking walkIn =
        booking(
            "2026-11-05T18:00:00Z",
            "2026-11-05T19:00:00Z",
            BookingStatus.CONFIRMED,
            PaymentStatus.NONE,
            null,
            Holder.walkIn("Carme Souto", null, "+34 600 111 222", Language.GL));
    String csv =
        csv(
            Language.GL,
            view(
                walkIn,
                "Piscina",
                "Nado libre",
                null,
                Map.of(),
                new StaffAccounts.Member("Lucía Rey", StaffAccounts.Role.STAFF)),
            view(
                walkIn,
                "Piscina",
                "Nado libre",
                null,
                Map.of(),
                new StaffAccounts.Member(null, StaffAccounts.Role.MANAGER)));

    String[] lines = csv.substring(1).split("\r\n");
    assertThat(lines[1])
        .as("sen correo; o teléfono, neutralizado como calquera texto")
        .startsWith("05/11/2026;19:00;20:00;Piscina;Nado libre;Carme Souto;;'+34 600 111 222;")
        .endsWith(";03/11/2026 10:00;Lucía Rey;;;");
    assertThat(lines[2]).as("sen nome na conta, o rol").endsWith(";03/11/2026 10:00;Xestión;;;");
  }

  @Test
  void formulasAreNeutralisedAndQuotesEscaped() {
    assertThat(BookingCsv.text("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    assertThat(BookingCsv.text("+34 600")).isEqualTo("'+34 600");
    assertThat(BookingCsv.text("-1")).isEqualTo("'-1");
    assertThat(BookingCsv.text("@a")).isEqualTo("'@a");
    assertThat(BookingCsv.text("a;b")).isEqualTo("\"a;b\"");
    assertThat(BookingCsv.text("liña\nnova")).isEqualTo("\"liña\nnova\"");
    assertThat(BookingCsv.text(null)).isEmpty();
    assertThat(BookingCsv.number(new BigDecimal("-3.25"))).isEqualTo("-3,25");
  }

  @Test
  void anEmptyExportStillHasTheHeader() {
    StringWriter out = new StringWriter();
    BookingCsv csv = new BookingCsv(out, Language.GL, NOW);
    csv.begin(0, List.of());
    assertThat(out.toString())
        .startsWith(BookingCsv.BOM + "Data;")
        .endsWith("Reservada o;Reservada por\r\n");
  }

  @Test
  void theFileNameCarriesTheMunicipalityAndThePeriod() {
    assertThat(
            BookingExportController.fileName(
                "naron", LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")))
        .isEqualTo("furar-naron-reservas-2026-11-01-2026-11-30.csv");
  }

  private static String csv(Language language, ManagedBooking... views) {
    StringWriter out = new StringWriter();
    BookingCsv csv = new BookingCsv(out, language, NOW);
    csv.begin(views.length, FIELDS);
    csv.write(List.of(views));
    return out.toString();
  }

  private static ManagedBooking view(
      Booking booking,
      String facility,
      String use,
      @Nullable String part,
      Map<String, CustomFieldValue> fields) {
    return view(booking, facility, use, part, fields, null);
  }

  private static ManagedBooking view(
      Booking booking,
      String facility,
      String use,
      @Nullable String part,
      Map<String, CustomFieldValue> fields,
      StaffAccounts.@Nullable Member bookedBy) {
    return new ManagedBooking(
        booking,
        facility,
        null,
        use,
        part,
        false,
        Set.of(),
        fields.isEmpty() ? CustomFieldValues.empty() : CustomFieldValues.fromStorage(fields),
        bookedBy,
        null);
  }

  private static Booking booking(
      String start,
      String end,
      BookingStatus status,
      PaymentStatus payment,
      @Nullable Integer cents,
      @Nullable Holder holder) {
    Instant created = Instant.parse("2026-11-03T09:00:00Z");
    return Booking.rehydrate(
        BookingId.newId(),
        new BookingCode("FUR-AB23"),
        new BookedSlot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            RentalMode.WHOLE,
            Instant.parse(start),
            Instant.parse(end),
            1),
        status,
        payment,
        cents == null ? Price.free() : Price.paid(cents),
        BookingRules.none(),
        Instant.parse(start),
        holder,
        holder == null || holder.hasAccount() ? null : "staff-sub",
        status == BookingStatus.CONFIRMED ? null : created,
        status == BookingStatus.CONFIRMED ? null : "x",
        0,
        created,
        created);
  }
}
