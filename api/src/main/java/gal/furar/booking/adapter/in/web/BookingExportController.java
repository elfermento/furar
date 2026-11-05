// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.BookingStatusValue;
import gal.furar.booking.application.port.in.BookingExportQuery;
import gal.furar.booking.application.port.in.BookingExportSink;
import gal.furar.booking.application.port.in.BookingFieldColumn;
import gal.furar.booking.application.port.in.ExportBookingsUseCase;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Descargar CSV» das reservas (maqueta 60), só a xestión. O CSV escríbese na resposta mentres se
 * len as reservas, por tandas, sen telas todas en memoria; as cabeceiras HTTP van ao empezar, así
 * que un erro do período ou do permiso aínda sae como problema RFC 9457.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/bookings")
@Tag(name = "bookings", description = "Reservas")
class BookingExportController {

  static final String CSV = "text/csv";

  private final ExportBookingsUseCase export;
  private final Clock clock;

  BookingExportController(ExportBookingsUseCase export, Clock clock) {
    this.export = export;
    this.clock = clock;
  }

  @GetMapping("/export")
  @Operation(
      operationId = "exportBookings",
      summary =
          "Descargar en CSV as reservas que empezan entre dous días (Europe/Madrid, incluídos;"
              + " como moito 12 meses, se non 422 booking.export-range-too-long), por instalación"
              + " e estado: UTF-8 con BOM, «;», coma decimal e as cabeceiras no idioma da petición")
  @ApiResponse(
      responseCode = "200",
      description = "CSV",
      content = @Content(mediaType = CSV, schema = @Schema(type = "string", format = "binary")))
  void export(
      @PathVariable String slug,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @Parameter(description = "Instalacións (baleiro: todas)")
          @RequestParam(name = "facilityId", required = false)
          @Nullable List<UUID> facilityIds,
      @Parameter(description = "Estados da reserva (baleiro: todos)")
          @RequestParam(required = false)
          @Nullable List<BookingStatusValue> status,
      HttpServletRequest request,
      HttpServletResponse response) {
    BookingExportQuery query =
        new BookingExportQuery(
            from,
            to,
            facilityIds == null ? Set.of() : Set.copyOf(facilityIds),
            status == null
                ? Set.of()
                : status.stream()
                    .map(s -> BookingStatus.valueOf(s.name()))
                    .collect(Collectors.toSet()));
    export.export(query, new Lazy(slug, query, request, response));
  }

  /** O nome do ficheiro: {@code furar-<concello>-reservas-<desde>-<ata>.csv}. */
  static String fileName(String slug, LocalDate from, LocalDate to) {
    return "furar-" + slug + "-reservas-" + from + "-" + to + ".csv";
  }

  /**
   * Abre a resposta só cando empeza a descarga (despois de validar e rexistrar): antes, un erro
   * aínda pode saír como problema.
   */
  private final class Lazy implements BookingExportSink {

    private final String slug;
    private final BookingExportQuery query;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private @Nullable BookingCsv csv;

    Lazy(
        String slug,
        BookingExportQuery query,
        HttpServletRequest request,
        HttpServletResponse response) {
      this.slug = slug;
      this.query = query;
      this.request = request;
      this.response = response;
    }

    @Override
    public void begin(long total, List<BookingFieldColumn> fields) {
      response.setContentType(CSV + ";charset=UTF-8");
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION,
          ContentDisposition.attachment()
              .filename(fileName(slug, query.from(), query.to()))
              .build()
              .toString());
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      try {
        Writer out =
            new BufferedWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        csv = new BookingCsv(out, Locales.language(request), clock.instant());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      csv.begin(total, fields);
    }

    @Override
    public void write(List<ManagedBooking> bookings) {
      if (csv != null) {
        csv.write(bookings);
      }
    }
  }
}
