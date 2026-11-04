// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha imaxe na nova orde: unha foto da biblioteca ({@code photoId}, co seu texto alternativo) ou
 * un debuxo ({@code drawing}), nunca os dous.
 */
public record ArrangedImageRequest(
    @Nullable UUID photoId,
    @Nullable @Size(max = 40) String drawing,
    @Nullable @Valid PhotoAltRequest alt) {}
