// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * O concello cancela unha reserva (P12): por onde avisa. Sen motivo escrito na fase 1.
 *
 * @param channels {@code EMAIL} envía o correo; {@code WEB}, a reserva sae cancelada polo concello
 *     nas da persoa; baleiro, sen aviso
 */
public record CancelBookingRequest(@NotNull Set<CancelChannelValue> channels) {}
