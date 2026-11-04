// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.adapter.in.web.dto.BookingCalendarResponse;
import gal.furar.booking.adapter.in.web.dto.BookingSortValue;
import gal.furar.booking.adapter.in.web.dto.BookingStatusValue;
import gal.furar.booking.adapter.in.web.dto.BookingsOverviewResponse;
import gal.furar.booking.adapter.in.web.dto.CalendarViewValue;
import gal.furar.booking.adapter.in.web.dto.CancelBookingRequest;
import gal.furar.booking.adapter.in.web.dto.ChangeBookingPaymentRequest;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingDetailResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingResponse;
import gal.furar.booking.adapter.in.web.dto.PaymentStatusValue;
import gal.furar.booking.application.port.in.BookingSearch;
import gal.furar.booking.application.port.in.BookingSort;
import gal.furar.booking.application.port.in.CalendarView;
import gal.furar.booking.application.port.in.ManageBookingUseCase;
import gal.furar.booking.application.port.in.ManageBookingsUseCase;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.PageResponse;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As reservas do concello para o persoal (plan do dominio 2, §2.2; maquetas 26 e 27): lista,
 * portada, detalle, calendario, cobro e cancelación. Ver e cobrar: xestión e persoal; cancelar: só
 * xestión. Unha reserva doutro concello non existe (404).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/bookings")
@Tag(name = "bookings", description = "Reservas")
class ManageBookingsController {

  private final ManageBookingsUseCase bookings;
  private final ManageBookingUseCase manage;

  ManageBookingsController(ManageBookingsUseCase bookings, ManageBookingUseCase manage) {
    this.bookings = bookings;
    this.manage = manage;
  }

  @GetMapping
  @Operation(
      operationId = "listBookings",
      summary =
          "Lista de xestión: filtros por días (Europe/Madrid, incluídos, sobre o inicio),"
              + " instalación, cobro e estado; busca por nome sen acentos ou por código;"
              + " as máis recentes primeiro ou por día e hora")
  PageResponse<ManagedBookingResponse> list(
      @PathVariable String slug,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      @Parameter(description = "Instalacións (baleiro: todas)")
          @RequestParam(name = "facilityId", required = false)
          @Nullable List<UUID> facilityIds,
      @Parameter(description = "Estados do cobro (NONE: gratis ou sen prezo; baleiro: todos)")
          @RequestParam(required = false)
          @Nullable List<PaymentStatusValue> payment,
      @Parameter(description = "Estados da reserva (baleiro: todos)")
          @RequestParam(required = false)
          @Nullable List<BookingStatusValue> status,
      @Parameter(description = "Nome ou correo de quen reserva (sen acentos) ou código")
          @RequestParam(required = false)
          @Size(max = 200)
          @Nullable String q,
      @RequestParam(defaultValue = "RECENT") BookingSortValue sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    BookingSearch search =
        new BookingSearch(
            from,
            to,
            facilityIds == null ? Set.of() : Set.copyOf(facilityIds),
            payment == null
                ? Set.of()
                : payment.stream()
                    .map(p -> PaymentStatus.valueOf(p.name()))
                    .collect(Collectors.toSet()),
            status == null
                ? Set.of()
                : status.stream()
                    .map(s -> BookingStatus.valueOf(s.name()))
                    .collect(Collectors.toSet()),
            q,
            BookingSort.valueOf(sort.name()),
            page,
            size);
    return PageResponse.from(bookings.search(search), BookingWebMapper::managed);
  }

  @GetMapping("/summary")
  @Operation(
      operationId = "getBookingsSummary",
      summary =
          "Portada de «Reservas»: as últimas reservadas e as pestanas de «Cobros» (pendentes de"
              + " pago e canceladas de hoxe aos próximos 30 días; a devolver, de calquera día),"
              + " coas contas e as primeiras de cada unha")
  BookingsOverviewResponse summary(
      @PathVariable String slug, @RequestParam(defaultValue = "3") @Min(1) @Max(20) int size) {
    return BookingWebMapper.overview(bookings.overview(size));
  }

  @GetMapping("/calendar")
  @Operation(
      operationId = "getBookingsCalendar",
      summary =
          "Calendario das reservas confirmadas: día e semana dunha instalación, cos bloques;"
              + " semana de todas, contas por día e hora; mes, contas por día")
  BookingCalendarResponse calendar(
      @PathVariable String slug,
      @RequestParam CalendarViewValue view,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @Parameter(description = "Unha instalación (sen ela: todas)") @RequestParam(required = false)
          @Nullable UUID facilityId) {
    return BookingWebMapper.calendar(
        bookings.calendar(CalendarView.valueOf(view.name()), date, facilityId));
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getBooking", summary = "O detalle dunha reserva do concello")
  @VersionedResource
  ResponseEntity<ManagedBookingDetailResponse> get(
      @PathVariable String slug, @PathVariable UUID id) {
    return detail(bookings.get(new BookingId(id)));
  }

  @PutMapping("/{id}/payment")
  @Operation(
      operationId = "changeBookingPayment",
      summary =
          "Marcar o cobro (If-Match): pagada, devolta ou desfacer; outro paso, 422"
              + " booking.payment-transition-invalid")
  @ApiResponse(responseCode = "200", description = "Marcado", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<ManagedBookingDetailResponse> payment(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ChangeBookingPaymentRequest request) {
    return detail(
        manage.changePayment(
            new BookingId(id),
            ETags.parseIfMatch(ifMatch),
            PaymentStatus.valueOf(request.status().name())));
  }

  @PostMapping("/{id}/cancel")
  @Operation(
      operationId = "cancelBooking",
      summary =
          "O concello cancela a reserva (If-Match) e di por onde avisa; se estaba pagada, queda"
              + " «a devolver»")
  @ApiResponse(responseCode = "200", description = "Cancelada", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<ManagedBookingDetailResponse> cancel(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody CancelBookingRequest request) {
    return detail(
        manage.cancel(
            new BookingId(id),
            ETags.parseIfMatch(ifMatch),
            request.channels().stream()
                .map(c -> BookingCancelled.Channel.valueOf(c.name()))
                .collect(Collectors.toSet())));
  }

  private static ResponseEntity<ManagedBookingDetailResponse> detail(ManagedBooking view) {
    return ResponseEntity.ok()
        .eTag(ETags.of(BookingWebMapper.version(view.booking())))
        .body(BookingWebMapper.detail(view));
  }
}
