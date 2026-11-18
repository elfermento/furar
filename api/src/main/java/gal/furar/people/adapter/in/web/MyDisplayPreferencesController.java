// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web;

import gal.furar.people.adapter.in.web.dto.DisplayPreferencesRequest;
import gal.furar.people.adapter.in.web.dto.DisplayPreferencesResponse;
import gal.furar.people.application.port.in.DisplayPreferencesUseCase;
import gal.furar.people.domain.DisplayPreferences;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * As preferencias de accesibilidade da conta da sesión (ADR-0031, revisión do 2026-11-11). Fóra de
 * {@code /municipalities/{slug}}: a conta é común a todos os concellos. Controlador fino: o permiso
 * vive no caso de uso.
 */
@RestController
@RequestMapping("/api/v1/me/display-preferences")
@Tag(name = "people", description = "Persoas a cargo")
class MyDisplayPreferencesController {

  private final DisplayPreferencesUseCase preferences;

  MyDisplayPreferencesController(DisplayPreferencesUseCase preferences) {
    this.preferences = preferences;
  }

  @GetMapping
  @Operation(
      operationId = "getMyDisplayPreferences",
      summary = "As preferencias de accesibilidade da conta; 204 se nunca se gardaron")
  @ApiResponse(responseCode = "200", description = "Gardadas", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "204", description = "Sen gardar")
  ResponseEntity<DisplayPreferencesResponse> mine() {
    return preferences
        .mine()
        .map(
            p ->
                ResponseEntity.ok(
                    new DisplayPreferencesResponse(
                        p.textSize(), p.highContrast(), p.reducedMotion())))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "saveMyDisplayPreferences",
      summary = "Gardar as preferencias de accesibilidade na conta")
  @ApiResponse(responseCode = "204", description = "Gardadas")
  void save(@Valid @RequestBody DisplayPreferencesRequest body) {
    preferences.save(
        new DisplayPreferences(body.textSize(), body.highContrast(), body.reducedMotion()));
  }
}
