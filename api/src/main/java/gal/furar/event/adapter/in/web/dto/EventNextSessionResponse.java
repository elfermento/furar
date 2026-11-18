// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Unha próxima sesión dunha actividade con horario (ADR-0041): o día e as horas. */
public record EventNextSessionResponse(LocalDate date, Instant startsAt, Instant endsAt) {}
