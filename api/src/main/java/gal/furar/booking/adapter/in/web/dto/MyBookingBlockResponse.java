// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Se a persoa está bloqueada por faltas neste concello (ADR-0032, punto 7).
 *
 * @param until o último día bloqueado (hora de Galicia); nulo se non está bloqueada
 * @param faults as faltas que causaron o bloqueo
 * @param windowDays en cantos días conta o concello as faltas
 */
public record MyBookingBlockResponse(
    boolean blocked, @Nullable LocalDate until, int faults, int windowDays) {}
