// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Deporte: do catálogo de Furar ({@code code}) ou do concello ({@code customSportId}); sempre un
 * dos dous.
 */
public record SportResponse(
    @Nullable String code, @Nullable UUID customSportId, SportLabelResponse label) {}
