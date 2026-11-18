// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.CreateOrganizationBookingRequest;
import gal.furar.booking.adapter.in.web.dto.MyBookingResponse;
import gal.furar.booking.adapter.in.web.dto.MyBookingScopeValue;
import gal.furar.booking.adapter.in.web.dto.MyOrganizationResponse;
import gal.furar.booking.adapter.in.web.dto.MySeriesRequestResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesRequestRequest;
import gal.furar.booking.adapter.in.web.dto.SlotTakenProblemResponse;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.MyOrganizationsUseCase;
import gal.furar.booking.application.port.in.ParticipantSelection;
import gal.furar.booking.application.port.in.ReserveCommand;
import gal.furar.booking.application.port.in.SeriesRequestCommand;
import gal.furar.booking.application.port.in.SeriesRequestView;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.PageResponse;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A persoa responsable dunha entidade, coa súa conta de sempre (ADR-0039, punto 10): as súas
 * entidades, as reservas da entidade (ver e cancelar), reservar no seu nome e as solicitudes de
 * tempada. Sen rol no concello: só sesión e correo verificado; sen ser responsable, 404.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/me/organizations")
@Tag(name = "bookings", description = "Reservas")
class MyOrganizationsController {

  private final MyOrganizationsUseCase organizations;

  MyOrganizationsController(MyOrganizationsUseCase organizations) {
    this.organizations = organizations;
  }

  @GetMapping
  @Operation(
      operationId = "listMyOrganizations",
      summary =
          "As entidades do concello das que a persoa é responsable (baleira se non o é de"
              + " ningunha; unha conta de traballo, baleira)")
  List<MyOrganizationResponse> list(@PathVariable String slug) {
    return organizations.mine().stream()
        .map(o -> new MyOrganizationResponse(o.id(), o.name()))
        .toList();
  }

  @GetMapping("/{organizationId}/bookings")
  @Operation(
      operationId = "listMyOrganizationBookings",
      summary =
          "As reservas da entidade (sesións e reservas extra): as próximas ou o historial,"
              + " coa entidade e, nas sesións, a tempada")
  PageResponse<MyBookingResponse> bookings(
      @PathVariable String slug,
      @PathVariable UUID organizationId,
      @RequestParam(defaultValue = "UPCOMING") MyBookingScopeValue scope,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(
        organizations.bookings(organizationId, scope == MyBookingScopeValue.UPCOMING, page, size),
        BookingWebMapper::mine);
  }

  @GetMapping("/{organizationId}/bookings/{id}")
  @Operation(operationId = "getMyOrganizationBooking", summary = "Unha reserva da entidade, co QR")
  @VersionedResource
  ResponseEntity<MyBookingResponse> booking(
      @PathVariable String slug, @PathVariable UUID organizationId, @PathVariable UUID id) {
    return entity(organizations.booking(organizationId, new BookingId(id)));
  }

  @PostMapping("/{organizationId}/bookings/{id}/cancel")
  @Operation(
      operationId = "cancelMyOrganizationBooking",
      summary =
          "Cancelar unha reserva da entidade (If-Match): unha sesión, ata que empeza e sen custo;"
              + " unha reserva extra, dentro do seu prazo. As horas quedan libres")
  @VersionedResource
  ResponseEntity<MyBookingResponse> cancel(
      @PathVariable String slug,
      @PathVariable UUID organizationId,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    return entity(
        organizations.cancel(organizationId, new BookingId(id), ETags.parseIfMatch(ifMatch)));
  }

  @PostMapping("/{organizationId}/bookings")
  @Operation(
      operationId = "createMyOrganizationBooking",
      summary =
          "Reservar no nome da entidade: as mesmas normas e prezo ca unha persoa, e o límite do"
              + " uso conta para a entidade (as sesións non contan). Sen usos por prazas."
              + " Idempotente coa cabeceira Idempotency-Key")
  @ApiResponse(responseCode = "201", description = "Reservada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "booking.slot-taken coas alternativas; booking.part-taken",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SlotTakenProblemResponse.class)))
  @VersionedResource
  ResponseEntity<MyBookingResponse> reserve(
      @PathVariable String slug,
      @PathVariable UUID organizationId,
      @Parameter(
              name = IdempotencyKey.HEADER,
              in = ParameterIn.HEADER,
              required = true,
              description = "Clave opaca por intención de reservar (un UUID)",
              schema =
                  @Schema(type = "string", minLength = 1, maxLength = IdempotencyKey.MAX_LENGTH))
          @RequestHeader(name = IdempotencyKey.HEADER, required = false)
          @Nullable String idempotencyKey,
      @Valid @RequestBody CreateOrganizationBookingRequest request) {
    IdempotencyKey key = IdempotencyKey.parse(idempotencyKey);
    MyBooking created =
        organizations.reserve(
            organizationId,
            new ReserveCommand(
                request.facilityId(),
                request.useId(),
                request.start(),
                request.slots(),
                request.partId(),
                key,
                request.customFields() == null ? Map.of() : request.customFields(),
                ParticipantSelection.SELF));
    return ResponseEntity.created(
            URI.create(
                "/api/v1/municipalities/"
                    + slug
                    + "/me/organizations/"
                    + organizationId
                    + "/bookings/"
                    + created.booking().id().value()))
        .eTag(ETags.of(BookingWebMapper.version(created)))
        .body(BookingWebMapper.mine(created));
  }

  @PostMapping("/{organizationId}/series-requests")
  @Operation(
      operationId = "createMySeriesRequest",
      summary =
          "Solicitar unha tempada para calquera instalación que se poida reservar (decide o"
              + " concello ao aprobar); como moito 5 pendentes por entidade")
  @ApiResponse(responseCode = "201", description = "Solicitada", useReturnTypeSchema = true)
  ResponseEntity<MySeriesRequestResponse> request(
      @PathVariable String slug,
      @PathVariable UUID organizationId,
      @Valid @RequestBody SeriesRequestRequest request) {
    SeriesRequestView created =
        organizations.request(
            organizationId,
            new SeriesRequestCommand(
                SeriesSchedule.of(
                    request.facilityId(),
                    request.useId(),
                    request.partId(),
                    request.weekdays(),
                    request.startMinute(),
                    request.endMinute(),
                    request.firstDate(),
                    request.lastDate()),
                request.note()));
    return ResponseEntity.created(
            URI.create(
                "/api/v1/municipalities/"
                    + slug
                    + "/me/organizations/"
                    + organizationId
                    + "/series-requests/"
                    + created.request().id()))
        .body(SeasonWebMapper.mine(created));
  }

  @GetMapping("/{organizationId}/series-requests")
  @Operation(
      operationId = "listMySeriesRequests",
      summary = "As solicitudes de tempada da entidade, da máis nova, coa mensaxe se se rexeitou")
  List<MySeriesRequestResponse> requests(
      @PathVariable String slug, @PathVariable UUID organizationId) {
    return organizations.requests(organizationId).stream().map(SeasonWebMapper::mine).toList();
  }

  @DeleteMapping("/{organizationId}/series-requests/{id}")
  @Operation(
      operationId = "withdrawMySeriesRequest",
      summary = "Retirar unha solicitude pendente; xa decidida, 409 booking.series-request-decided")
  @ApiResponse(responseCode = "204", description = "Retirada")
  ResponseEntity<Void> withdraw(
      @PathVariable String slug, @PathVariable UUID organizationId, @PathVariable UUID id) {
    organizations.withdraw(organizationId, id);
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<MyBookingResponse> entity(MyBooking view) {
    return ResponseEntity.ok()
        .eTag(ETags.of(BookingWebMapper.version(view)))
        .body(BookingWebMapper.mine(view));
  }
}
