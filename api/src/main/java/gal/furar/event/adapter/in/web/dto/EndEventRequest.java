// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Rematar antes unha actividade (ADR-0041): desde ese día xa non hai sesións. */
public record EndEventRequest(@NotNull LocalDate from) {}
