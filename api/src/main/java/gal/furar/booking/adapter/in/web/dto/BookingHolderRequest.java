// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Para quen é unha reserva que fai o persoal (persoa sen conta): o nome e, se os dá, o correo (para
 * a confirmación) e o teléfono.
 */
public record BookingHolderRequest(
    @NotBlank @Size(max = 200) String name,
    @Nullable @Email @Size(min = 3, max = 254) String email,
    @Nullable @Size(min = 3, max = 30) @Pattern(regexp = "^[0-9+() .-]+$") String phone) {}
