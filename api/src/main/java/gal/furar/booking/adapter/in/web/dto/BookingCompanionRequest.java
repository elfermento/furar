// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un acompañante que o persoal escribe só para unha reserva: nome e apelidos e ano de nacemento.
 * Sen correo: o correo é só de quen ten a reserva.
 */
public record BookingCompanionRequest(
    @NotBlank @Size(max = 200) String name, @NotNull @Min(1900) @Max(2200) Integer birthYear) {}
