// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.adapter.in.web.dto.BookForRequest;
import gal.furar.booking.adapter.in.web.dto.BookingCalendarResponse;
import gal.furar.booking.adapter.in.web.dto.BookingHolderRequest;
import gal.furar.booking.adapter.in.web.dto.BookingSortValue;
import gal.furar.booking.adapter.in.web.dto.BookingStatusValue;
import gal.furar.booking.adapter.in.web.dto.BookingsOverviewResponse;
import gal.furar.booking.adapter.in.web.dto.CalendarViewValue;
import gal.furar.booking.adapter.in.web.dto.CancelBookingRequest;
import gal.furar.booking.adapter.in.web.dto.ChangeBookingPaymentRequest;
import gal.furar.booking.adapter.in.web.dto.ChangeBookingRequest;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingDetailResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedBookingResponse;
import gal.furar.booking.adapter.in.web.dto.PaymentStatusValue;
import gal.furar.booking.adapter.in.web.dto.SlotTakenProblemResponse;
import gal.furar.booking.application.port.in.BookForUseCase;
import gal.furar.booking.application.port.in.BookingSearch;
import gal.furar.booking.application.port.in.BookingSort;
import gal.furar.booking.application.port.in.CalendarView;
import gal.furar.booking.application.port.in.ChangeBookingByStaffCommand;
import gal.furar.booking.application.port.in.ManageBookingUseCase;
import gal.furar.booking.application.port.in.ManageBookingsUseCase;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.ReserveForCommand;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.shared.domain.Language;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.Locales;
import gal.furar.shared.web.PageResponse;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
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
 * portada, detalle, calendario, cobro e cancelación, e reservar no nome doutra persoa e cambiar
 * (maqueta 58). Ver, cobrar, reservar e cambiar: xestión e persoal; cancelar: só xestión. Unha
 * reserva doutro concello non existe (404).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/bookings")
@Tag(name = "bookings", description = "Reservas")
class ManageBookingsController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  private final ManageBookingsUseCase bookings;
  private final ManageBookingUseCase manage;
  private final BookForUseCase bookFor;

  ManageBookingsController(
      ManageBookingsUseCase bookings, ManageBookingUseCase manage, BookForUseCase bookFor) {
    this.bookings = bookings;
    this.manage = manage;
    this.bookFor = bookFor;
  }

  @PostMapping
  @Operation(
      operationId = "createBooking",
      summary =
          "O persoal reserva no nome dunha persoa sen conta (mostrador ou teléfono): o mesmo oco"
              + " ca ao reservar a persoa, o nome e, se os dá, o correo (para a confirmación) e o"
              + " teléfono; se xa pagou, queda pagada. Idempotente coa cabeceira Idempotency-Key")
  @ApiResponse(
      responseCode = "201",
      description = "Reservada, ou xa reservada antes con esta clave e esta petición",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "booking.slot-taken coas alternativas; booking.part-taken",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SlotTakenProblemResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "booking.idempotency-key-reused; as normas (booking.too-soon, booking.too-far,"
              + " booking.too-long…)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @VersionedResource
  ResponseEntity<ManagedBookingDetailResponse> create(
      @PathVariable String slug,
      @Parameter(
              name = IdempotencyKey.HEADER,
              in = ParameterIn.HEADER,
              required = true,
              description = "Clave opaca por intención de reservar (un UUID)",
              schema =
                  @Schema(type = "string", minLength = 1, maxLength = IdempotencyKey.MAX_LENGTH))
          @RequestHeader(name = IdempotencyKey.HEADER, required = false)
          @Nullable String idempotencyKey,
      @Valid @RequestBody BookForRequest request,
      HttpServletRequest http) {
    IdempotencyKey key = IdempotencyKey.parse(idempotencyKey);
    Language language =
        request.language() == null
            ? Locales.language(http)
            : Language.fromCode(request.language()).orElseThrow();
    BookingHolderRequest h = request.holder();
    ManagedBooking created =
        bookFor.reserveFor(
            new ReserveForCommand(
                request.facilityId(),
                request.useId(),
                request.start(),
                request.slots(),
                request.partId(),
                Holder.walkIn(h.name().strip(), blank(h.email()), blank(h.phone()), language),
                Boolean.TRUE.equals(request.paid()),
                key,
                request.customFields()));
    URI location =
        URI.create(
            "/api/v1/municipalities/" + slug + "/bookings/" + created.booking().id().value());
    return ResponseEntity.created(location)
        .eTag(ETags.of(BookingWebMapper.version(created.booking())))
        .body(BookingWebMapper.detail(created));
  }

  @PutMapping("/{id}")
  @Operation(
      operationId = "changeBooking",
      summary =
          "O persoal cambia a reserva co mesmo formulario (If-Match): outro oco da mesma"
              + " instalación (sen o prazo da persoa), para quen é (só nas que fixo o persoal), o"
              + " pago e os campos propios; se se marca, avísase á persoa por correo")
  @ApiResponse(responseCode = "200", description = "Cambiada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description =
          "booking.slot-taken coas alternativas; booking.part-taken; booking.not-cancellable",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SlotTakenProblemResponse.class)))
  @VersionedResource
  ResponseEntity<ManagedBookingDetailResponse> change(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ChangeBookingRequest request) {
    BookingHolderRequest h = request.holder();
    return detail(
        bookFor.change(
            new ChangeBookingByStaffCommand(
                new BookingId(id),
                ETags.parseIfMatch(ifMatch),
                request.useId(),
                request.start(),
                request.slots(),
                request.partId(),
                h == null
                    ? null
                    : new ChangeBookingByStaffCommand.Contact(
                        h.name().strip(), blank(h.email()), blank(h.phone())),
                request.paid(),
                !Boolean.FALSE.equals(request.notifyPerson()),
                request.customFields())));
  }

  /** Un texto baleiro vale o mesmo que non mandalo. */
  private static @Nullable String blank(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
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
