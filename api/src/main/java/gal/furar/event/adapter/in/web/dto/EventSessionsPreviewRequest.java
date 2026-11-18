// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A vista previa das sesións dunha actividade (ADR-0041): onde e o horario. */
public record EventSessionsPreviewRequest(
    @Nullable UUID facilityId, @Valid @NotNull EventScheduleRequest schedule) {}
