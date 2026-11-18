// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulario {@code multipart/form-data} de «Nova incidencia» (o persoal): a instalación, que pasa,
 * o texto (ata 500) e ata tres fotos (JPEG ou PNG, 8 MB cada unha).
 */
public record RecordIncidentRequest(
    @NotNull UUID facilityId,
    @NotNull IncidentTypeValue type,
    @NotBlank @Size(max = 500) String description,
    @Size(max = 3) @ArraySchema(schema = @Schema(type = "string", format = "binary"))
        @Nullable List<MultipartFile> photos) {}
