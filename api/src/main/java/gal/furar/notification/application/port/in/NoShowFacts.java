// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * O que conta o correo dunha falta ademais da reserva (ADR-0032, punto 10): cantas leva a persoa e
 * cando se bloquea.
 *
 * @param faults faltas na xanela do concello; 0 coa penalización apagada (sen a frase do bloqueo)
 * @param threshold faltas que bloquean
 * @param windowDays en cantos días
 * @param blockDays días sen reservar por internet
 * @param blockedUntil o último día bloqueado, se a persoa está bloqueada
 */
public record NoShowFacts(
    int faults, int threshold, int windowDays, int blockDays, @Nullable LocalDate blockedUntil) {}
