// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Todas as imaxes da instalación na nova orde (a primeira é a portada); baleira = a suxerida. O
 * límite de 12 compróbao o dominio (422 {@code facility.photo-limit}); este só corta abusos.
 */
public record ArrangeImagesRequest(
    @NotNull @Size(max = 50) List<@NotNull @Valid ArrangedImageRequest> images) {}
