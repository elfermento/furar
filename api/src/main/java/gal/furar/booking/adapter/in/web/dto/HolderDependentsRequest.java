// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * De quen se piden as persoas a cargo desde o mostrador: o correo que escribe o persoal ao reservar
 * ou a reserva que está cambiando. Un dos dous. O correo vai no corpo, non no URL.
 */
public record HolderDependentsRequest(
    @Nullable @Email @Size(min = 3, max = 254) String email, @Nullable UUID bookingId) {}
