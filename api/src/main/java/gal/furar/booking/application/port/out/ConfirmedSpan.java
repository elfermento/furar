// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** O uso e o período {@code [start, end)} dunha reserva confirmada dun correo. */
public record ConfirmedSpan(UUID useId, Instant start, Instant end) {}
