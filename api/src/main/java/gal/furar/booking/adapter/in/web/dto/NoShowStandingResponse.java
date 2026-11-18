// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * As faltas que leva a persoa dunha reserva con «non presentado», coa penalización do concello
 * acesa (ADR-0032, punto 7): o aviso do persoal.
 *
 * @param faults faltas que contan nos últimos {@code windowDays} días
 * @param threshold faltas que bloquean
 * @param blockDays días sen reservar por internet
 * @param blockedUntil o último día bloqueado (hora de Galicia); nulo se non quedou bloqueada
 */
public record NoShowStandingResponse(
    int faults, int windowDays, int threshold, int blockDays, @Nullable LocalDate blockedUntil) {}
