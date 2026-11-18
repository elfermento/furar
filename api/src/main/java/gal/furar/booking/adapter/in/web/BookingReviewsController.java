// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.HideReviewRequest;
import gal.furar.booking.adapter.in.web.dto.ManagedReviewResponse;
import gal.furar.booking.adapter.in.web.dto.ManagedReviewsResponse;
import gal.furar.booking.adapter.in.web.dto.MyReviewResponse;
import gal.furar.booking.adapter.in.web.dto.PublicReviewResponse;
import gal.furar.booking.adapter.in.web.dto.ReviewBookingRequest;
import gal.furar.booking.adapter.in.web.dto.ReviewSortValue;
import gal.furar.booking.adapter.in.web.dto.ReviewStarsCountResponse;
import gal.furar.booking.adapter.in.web.dto.ReviewSummaryResponse;
import gal.furar.booking.application.port.in.FacilityReviewsUseCase;
import gal.furar.booking.application.port.in.ManageReviewsUseCase;
import gal.furar.booking.application.port.in.ManageReviewsUseCase.ManagedReview;
import gal.furar.booking.application.port.in.ReviewMyBookingUseCase;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Review;
import gal.furar.booking.domain.ReviewOrder;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * As valoracións (ADR-0037): valorar unha reserva propia (sesión e correo verificado; outra persoa
 * → 404), a nota e a lista pública dunha instalación (sen sesión e sen ningún dato de quen valorou)
 * e a táboa da xestión con «Ocultar comentario» (o permiso, no caso de uso).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}")
@Tag(name = "bookings", description = "Reservas")
class BookingReviewsController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  private final ReviewMyBookingUseCase mine;
  private final FacilityReviewsUseCase facilityReviews;
  private final ManageReviewsUseCase manage;

  BookingReviewsController(
      ReviewMyBookingUseCase mine,
      FacilityReviewsUseCase facilityReviews,
      ManageReviewsUseCase manage) {
    this.mine = mine;
    this.facilityReviews = facilityReviews;
    this.manage = manage;
  }

  @PostMapping("/me/bookings/{id}/review")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "reviewMyBooking",
      summary =
          "Valorar unha reserva propia, pasada e á que se foi, de 1 a 5 estrelas e cun comentario"
              + " opcional (300 caracteres); unha por reserva e sen cambios")
  @ApiResponse(responseCode = "201", description = "Valorada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "booking.review-exists (xa valorada)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "booking.review-not-allowed (non rematou, cancelada, falta ou sen validar con control de"
              + " entrada); booking.review-invalid (comentario de máis de 300 caracteres)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  MyReviewResponse review(
      @PathVariable String slug,
      @PathVariable UUID id,
      @Valid @RequestBody ReviewBookingRequest request) {
    Review review = mine.review(new BookingId(id), request.stars(), request.comment());
    return new MyReviewResponse(review.stars(), review.comment(), review.createdAt());
  }

  @GetMapping("/public/facilities/{id}/reviews/summary")
  @PublicEndpoint
  @Operation(
      operationId = "getFacilityReviewSummary",
      summary =
          "A nota media dunha instalación e cantas valoracións ten (tamén as de comentario"
              + " oculto); 404 se a instalación non se ve")
  ReviewSummaryResponse summary(@PathVariable String slug, @PathVariable UUID id) {
    FacilityReviewsUseCase.Summary summary = facilityReviews.summary(id);
    return new ReviewSummaryResponse(summary.reviews(), summary.average());
  }

  @GetMapping("/public/facilities/{id}/reviews")
  @PublicEndpoint
  @Operation(
      operationId = "listFacilityReviews",
      summary =
          "As valoracións dunha instalación, por páxinas: nota, data e comentario se non está"
              + " oculto; nunca quen valorou. 404 se a instalación non se ve")
  PageResponse<PublicReviewResponse> list(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "NEWEST") ReviewSortValue sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    return PageResponse.from(
        facilityReviews.list(id, ReviewOrder.valueOf(sort.name()), page, size),
        r -> new PublicReviewResponse(r.id(), r.stars(), r.comment(), r.createdAt()));
  }

  @GetMapping("/booking-reviews")
  @Operation(
      operationId = "listManagedReviews",
      summary =
          "As valoracións feitas entre from e to (incluídos, Europe/Madrid), das máis novas ás máis"
              + " vellas, cos filtros dos indicadores e o de nota; co comentario aínda que estea"
              + " oculto")
  @ApiResponse(responseCode = "200", description = "A páxina", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "422",
      description = "booking.review-invalid (período ou nota imposibles)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  ManagedReviewsResponse manage(
      @PathVariable String slug,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Nullable UUID facilityId,
      @RequestParam(required = false) @Nullable List<UUID> useIds,
      @RequestParam(required = false) @Nullable List<Integer> stars,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    ManageReviewsUseCase.Page result =
        manage.list(
            new ManageReviewsUseCase.Query(
                from,
                to,
                facilityId,
                useIds == null ? Set.of() : Set.copyOf(useIds),
                stars == null ? Set.of() : Set.copyOf(stars)),
            page,
            size);
    return new ManagedReviewsResponse(
        result.items().stream().map(BookingReviewsController::managed).toList(),
        result.page(),
        result.size(),
        result.total(),
        result.withComment(),
        IntStream.iterate(Review.MAX_STARS, s -> s >= Review.MIN_STARS, s -> s - 1)
            .mapToObj(s -> new ReviewStarsCountResponse(s, result.byStars().getOrDefault(s, 0L)))
            .toList());
  }

  @GetMapping("/booking-reviews/{id}")
  @Operation(operationId = "getManagedReview", summary = "Unha valoración para a xestión")
  ManagedReviewResponse get(@PathVariable String slug, @PathVariable UUID id) {
    return managed(manage.get(id));
  }

  @PutMapping("/booking-reviews/{id}/hidden")
  @Operation(
      operationId = "hideReviewComment",
      summary =
          "Ocultar o comentario (deixa de verse en público, a nota segue contando) ou volver"
              + " mostralo; queda no rexistro de actividade sen o texto. Repetir non cambia nada")
  ManagedReviewResponse hide(
      @PathVariable String slug,
      @PathVariable UUID id,
      @Valid @RequestBody HideReviewRequest request) {
    return managed(manage.hide(id, request.hidden()));
  }

  private static ManagedReviewResponse managed(ManagedReview view) {
    Review r = view.review();
    return new ManagedReviewResponse(
        r.id(),
        r.facilityId(),
        view.facilityName(),
        r.useId(),
        view.useName(),
        r.stars(),
        r.comment(),
        r.hidden(),
        r.createdAt());
  }
}
