// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** Acender ou apagar os recordatorios por correo neste concello (ADR-0035). */
public record ReminderSettingsRequest(@NotNull Boolean enabled) {}
