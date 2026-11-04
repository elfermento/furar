// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;

/** «Como se paga» e «Como se devolve» nesta instalación (con {@code If-Match}). */
public record UpdateFacilityPaymentNotesRequest(
    @Nullable @Valid FacilityNoteRequest payment, @Nullable @Valid FacilityNoteRequest refund) {}
