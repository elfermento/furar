// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Unha nota da instalación en gl e es; as dúas baleiras = sen nota. */
public record FacilityNoteRequest(
    @Nullable @Size(max = 500) String gl, @Nullable @Size(max = 500) String es) {}
