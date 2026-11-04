// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulario {@code multipart/form-data} da subida á biblioteca: o ficheiro (JPEG ou PNG, 8 MB).
 */
public record UploadLibraryPhotoRequest(
    @NotNull @Schema(type = "string", format = "binary") MultipartFile file) {}
