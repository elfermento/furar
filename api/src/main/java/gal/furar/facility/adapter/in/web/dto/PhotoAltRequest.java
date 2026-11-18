// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Texto alternativo en gl e es (os dous obrigatorios: compróbao o dominio, con erro por campo). */
public record PhotoAltRequest(
    @Nullable @Size(max = 250) String gl, @Nullable @Size(max = 250) String es) {}
