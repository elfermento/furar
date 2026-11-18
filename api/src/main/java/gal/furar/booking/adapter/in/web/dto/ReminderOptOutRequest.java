// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A proba da ligazón «Non quero recordatorios» do correo, tal como vén (ADR-0035). */
public record ReminderOptOutRequest(@NotBlank @Size(max = 200) String token) {}
