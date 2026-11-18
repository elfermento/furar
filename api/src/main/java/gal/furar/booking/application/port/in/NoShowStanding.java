// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Como queda a persoa despois dunha falta, coa penalización acesa (ADR-0032, punto 7): cantas leva
 * na xanela, cos números do concello, e ata que día (o último bloqueado, hora de Galicia) non pode
 * reservar por internet, se quedou bloqueada.
 */
public record NoShowStanding(
    int faults, int windowDays, int threshold, int blockDays, @Nullable LocalDate blockedUntil) {}
