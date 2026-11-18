// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un deporte da tarxeta Datos: {@code code} do catálogo ou {@code customSportId} dun deporte do
 * concello, só un dos dous.
 */
public record SportRefRequest(
    @Nullable @Size(max = 40) String code, @Nullable UUID customSportId) {}
