// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.ReminderOptOutRequest;
import gal.furar.booking.adapter.in.web.dto.ReminderSettingsRequest;
import gal.furar.booking.adapter.in.web.dto.ReminderSettingsResponse;
import gal.furar.booking.application.port.in.ReminderPreferencesUseCase;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Os recordatorios por correo da reserva (ADR-0035): a baixa desde a ligazón do propio correo, sen
 * sesión, e o interruptor da persoa con sesión neste concello. Controlador fino: o permiso vive no
 * caso de uso.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}")
@Tag(name = "bookings", description = "Reservas")
class ReminderOptOutController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  private final ReminderPreferencesUseCase reminders;

  ReminderOptOutController(ReminderPreferencesUseCase reminders) {
    this.reminders = reminders;
  }

  @PostMapping("/public/reminder-opt-out")
  @PublicEndpoint
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "optOutOfReminders",
      summary =
          "«Non quero recordatorios»: coa proba asinada da ligazón do correo, sen sesión, o correo"
              + " desa reserva deixa de recibir recordatorios neste concello. Repetir non cambia"
              + " nada")
  @ApiResponse(responseCode = "204", description = "Feito")
  @ApiResponse(
      responseCode = "410",
      description = "booking.reminder-link-expired",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description = "booking.reminder-link-invalid",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  void optOut(@PathVariable String slug, @Valid @RequestBody ReminderOptOutRequest request) {
    reminders.optOut(request.token());
  }

  @GetMapping("/me/reminders")
  @Operation(
      operationId = "getMyReminders",
      summary = "Se o correo da persoa recibe recordatorios das súas reservas neste concello")
  ReminderSettingsResponse mine(@PathVariable String slug) {
    return new ReminderSettingsResponse(reminders.remindersEnabled());
  }

  @PutMapping("/me/reminders")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "setMyReminders",
      summary = "Acender ou apagar os recordatorios por correo das reservas neste concello")
  @ApiResponse(responseCode = "204", description = "Gardado")
  void set(@PathVariable String slug, @Valid @RequestBody ReminderSettingsRequest request) {
    reminders.setRemindersEnabled(Boolean.TRUE.equals(request.enabled()));
  }
}
