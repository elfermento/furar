// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.MyBookingBlockResponse;
import gal.furar.booking.application.port.in.MyBookingBlockUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O bloqueo por faltas da persoa da sesión no concello (ADR-0032, punto 7): a pantalla de reservar
 * avísaa de ata cando non pode reservar por internet.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/me/booking-block")
@Tag(name = "bookings", description = "Reservas")
class MyBookingBlockController {

  private final MyBookingBlockUseCase blocks;

  MyBookingBlockController(MyBookingBlockUseCase blocks) {
    this.blocks = blocks;
  }

  @GetMapping
  @Operation(
      operationId = "getMyBookingBlock",
      summary =
          "Se a persoa non pode reservar por internet neste concello por faltas: ata que día e"
              + " cantas faltas")
  MyBookingBlockResponse get(@PathVariable String slug) {
    MyBookingBlockUseCase.Block block = blocks.block();
    return new MyBookingBlockResponse(
        block.blocked(), block.until(), block.faults(), block.windowDays());
  }
}
