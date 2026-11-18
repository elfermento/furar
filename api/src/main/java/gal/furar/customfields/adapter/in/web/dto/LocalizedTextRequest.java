// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Texto en galego e castelán. */
public record LocalizedTextRequest(
    @NotBlank @Size(max = 500) String gl, @NotBlank @Size(max = 500) String es) {}
