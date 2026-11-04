// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldUsageResponse(
    @Schema(example = "MUNICIPALITY") String target,
    boolean required,
    @Schema(allowableValues = {"PUBLIC", "OWNER", "STAFF"}) String visibility) {}
