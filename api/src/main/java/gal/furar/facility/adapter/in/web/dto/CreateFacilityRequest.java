// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** «Nova instalación»: só nome e enderezo. */
public record CreateFacilityRequest(
    @NotBlank @Size(max = 100) String name, @Nullable @Valid AddressRequest address) {}
