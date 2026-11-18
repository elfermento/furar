// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.MyOpenMatchResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchRequest;
import gal.furar.booking.adapter.in.web.dto.OpenMatchStatusRequest;
import gal.furar.booking.application.port.in.MyOpenMatch;
import gal.furar.booking.application.port.in.OpenMatchUseCase;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.OpenMatchStatus;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buscar xente para xogar para a persoa con sesión e correo verificado (ADR-0040): quen ten a
 * reserva ábrea, edítaa, deixa de buscar e decide as solicitudes; quen quere xogar únese, pídeo ou
 * sae. Sen rol no concello. As respostas levan {@code X-Robots-Tag: noindex}.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/me")
@Tag(name = "bookings", description = "Reservas")
class MyOpenMatchesController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  private final OpenMatchUseCase matches;

  MyOpenMatchesController(OpenMatchUseCase matches) {
    this.matches = matches;
  }

  @PutMapping("/bookings/{bookingId}/open-match")
  @Operation(
      operationId = "saveMyOpenMatch",
      summary =
          "Buscar xente para xogar nunha reserva propia confirmada de pista enteira ou por partes"
              + " (201) ou editala (200, If-Match da partida); unha pechada volve buscar. Só"
              + " mentres a reserva se pode cancelar de balde e antes do prazo do concello. Publica"
              + " o nome completo de quen organiza, nunca o correo")
  @ApiResponse(responseCode = "200", description = "Editada", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "201", description = "Aberta", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "404",
      description = "booking.matches-off (función apagada); booking.not-found (reserva allea)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "booking.match-not-allowed; booking.match-seats-use; booking.match-name-required (o"
              + " token non trae nome); booking.match-too-late (params.deadline);"
              + " booking.match-counts-invalid; booking.match-maximum-below-count (params.count)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @VersionedResource
  ResponseEntity<MyOpenMatchResponse> save(
      @PathVariable String slug,
      @PathVariable UUID bookingId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
      @Valid @RequestBody OpenMatchRequest request) {
    OpenMatchUseCase.Saved saved =
        matches.save(
            new BookingId(bookingId),
            ifMatch == null || ifMatch.isBlank() ? null : ETags.parseIfMatch(ifMatch),
            OpenMatchWebMapper.command(request));
    return respond(slug, saved.match(), saved.created());
  }

  @PutMapping("/bookings/{bookingId}/open-match/status")
  @Operation(
      operationId = "changeMyOpenMatchStatus",
      summary =
          "«Deixar de buscar» (CLOSED: xa non se une ninguén, deixa de verse e bórranse as"
              + " solicitudes pendentes; a reserva e as persoas seguen) ou volver buscar (OPEN, coas"
              + " comprobacións de abrir)")
  @ApiResponse(responseCode = "200", description = "Cambiada", useReturnTypeSchema = true)
  ResponseEntity<MyOpenMatchResponse> status(
      @PathVariable String slug,
      @PathVariable UUID bookingId,
      @Valid @RequestBody OpenMatchStatusRequest request) {
    MyOpenMatch changed =
        matches.changeStatus(
            new BookingId(bookingId), OpenMatchStatus.valueOf(request.status().name()));
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(OpenMatchWebMapper.mine(changed));
  }

  @GetMapping("/open-matches")
  @Operation(
      operationId = "listMyOpenMatches",
      summary =
          "As partidas que organizo ou nas que estou ou pedín entrar, aínda sen rematar, da que"
              + " empeza antes")
  ResponseEntity<List<MyOpenMatchResponse>> list(@PathVariable String slug) {
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(matches.mine().stream().map(OpenMatchWebMapper::mine).toList());
  }

  @GetMapping("/open-matches/{id}")
  @Operation(
      operationId = "getMyOpenMatch",
      summary =
          "Unha partida co meu papel: quen está dentro véa en calquera estado e cos nomes; o"
              + " resto, só mentres se ve en público (se non, 404 booking.match-not-found)")
  @VersionedResource
  ResponseEntity<MyOpenMatchResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    MyOpenMatch found = matches.get(id);
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .eTag(ETags.of(found.view().match().version()))
        .body(OpenMatchWebMapper.mine(found));
  }

  @PostMapping("/open-matches/{id}/players")
  @Operation(
      operationId = "joinOpenMatch",
      summary =
          "Unirme (sen aprobación) ou pedilo (con aprobación: correo a quen organiza); 201, ou"
              + " 200 se xa estaba ou o pedira")
  @ApiResponse(responseCode = "200", description = "Xa estaba", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "201", description = "Dentro ou pedido", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "403",
      description = "booking.staff-account; booking.blocked-no-show",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "404",
      description = "booking.matches-off; booking.match-not-found (non existe ou oculta)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description =
          "booking.match-closed; booking.match-own; booking.match-rejected; booking.match-full",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description = "booking.match-name-required",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  ResponseEntity<MyOpenMatchResponse> join(@PathVariable String slug, @PathVariable UUID id) {
    OpenMatchUseCase.Joined joined = matches.join(id);
    if (!joined.created()) {
      return ResponseEntity.ok()
          .headers(OpenMatchWebMapper.noindex())
          .body(OpenMatchWebMapper.mine(joined.match()));
    }
    return ResponseEntity.created(location(slug, id))
        .headers(OpenMatchWebMapper.noindex())
        .body(OpenMatchWebMapper.mine(joined.match()));
  }

  @DeleteMapping("/open-matches/{id}/players/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "leaveOpenMatch",
      summary =
          "Saír ou retirar a solicitude, ata que empeza (despois, 409 booking.match-started)."
              + " Saír despois do prazo non cancela nada")
  void leave(@PathVariable String slug, @PathVariable UUID id) {
    matches.leave(id);
  }

  @PostMapping("/open-matches/{id}/players/{playerId}/accept")
  @Operation(
      operationId = "acceptOpenMatchRequest",
      summary =
          "Quen organiza acepta unha solicitude pendente (correo a quen a pediu); 409"
              + " booking.match-full sen sitio, booking.match-player-decided xa decidida")
  ResponseEntity<MyOpenMatchResponse> accept(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID playerId) {
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(OpenMatchWebMapper.mine(matches.accept(id, playerId)));
  }

  @PostMapping("/open-matches/{id}/players/{playerId}/reject")
  @Operation(
      operationId = "rejectOpenMatchRequest",
      summary =
          "Quen organiza rexeita unha solicitude pendente (correo a quen a pediu; non pode pedilo"
              + " outra vez)")
  ResponseEntity<MyOpenMatchResponse> reject(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID playerId) {
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(OpenMatchWebMapper.mine(matches.reject(id, playerId)));
  }

  private static ResponseEntity<MyOpenMatchResponse> respond(
      String slug, MyOpenMatch match, boolean created) {
    UUID id = match.view().match().id();
    ResponseEntity.BodyBuilder builder =
        created ? ResponseEntity.created(location(slug, id)) : ResponseEntity.ok();
    return builder
        .headers(OpenMatchWebMapper.noindex())
        .eTag(ETags.of(match.view().match().version()))
        .body(OpenMatchWebMapper.mine(match));
  }

  private static URI location(String slug, UUID id) {
    return URI.create("/api/v1/municipalities/" + slug + "/me/open-matches/" + id);
  }
}
