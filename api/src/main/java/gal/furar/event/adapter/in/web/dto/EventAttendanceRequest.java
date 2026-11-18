// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Gardar a lista dunha sesión (ADR-0041): presente, ausente ou (nulo, só ao corrixir) sen marca. A
 * primeira vez, quen non vén aquí queda presente.
 */
public record EventAttendanceRequest(
    @NotNull @Size(max = 10_000) @Valid List<AttendanceMarkRequest> marks) {

  /** Unha marca. */
  public record AttendanceMarkRequest(@NotNull UUID registrationId, @Nullable Boolean present) {}
}
