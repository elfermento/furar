// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Cantas reservas confirmadas empezan nese día (e nesa hora), en Europe/Madrid.
 *
 * @param hour de 0 a 23, só nas contas por hora
 */
public record StartCount(LocalDate date, @Nullable Integer hour, long count) {}
