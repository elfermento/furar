// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.BookingOpenMatchResponse;
import gal.furar.booking.adapter.in.web.dto.HideOpenMatchRequest;
import gal.furar.booking.application.port.in.ManageOpenMatchUseCase;
import gal.furar.booking.domain.BookingId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A partida aberta dunha reserva desde a xestión (ADR-0040, punto 8): ocultala ou volver mostrala
 * (o permiso {@code booking.open-matches}, no caso de uso). Os números vense no detalle da reserva.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/bookings")
@Tag(name = "bookings", description = "Reservas")
class ManageOpenMatchesController {

  private final ManageOpenMatchUseCase matches;

  ManageOpenMatchesController(ManageOpenMatchUseCase matches) {
    this.matches = matches;
  }

  @PutMapping("/{bookingId}/open-match/hidden")
  @Operation(
      operationId = "hideOpenMatch",
      summary =
          "Ocultar a partida aberta da reserva (deixa de verse en público, ninguén novo se une e o"
              + " prazo non a cancela; correo a quen organiza) ou volver mostrala (sen correo)."
              + " Queda no rexistro de actividade sen título, descrición nin nomes. Repetir non"
              + " cambia nada")
  BookingOpenMatchResponse hide(
      @PathVariable String slug,
      @PathVariable UUID bookingId,
      @Valid @RequestBody HideOpenMatchRequest request) {
    return Objects.requireNonNull(
        OpenMatchWebMapper.booking(matches.hide(new BookingId(bookingId), request.hidden())));
  }
}
