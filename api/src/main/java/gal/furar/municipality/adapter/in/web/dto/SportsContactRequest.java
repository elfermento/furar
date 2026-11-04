// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Contacto do servizo de deportes enteiro (con {@code If-Match}); baleiro = sen ese dato. */
public record SportsContactRequest(
    @Nullable @Size(max = 200) String name,
    @Nullable @Size(max = 200) String address,
    @Nullable @Size(max = 30) String phone,
    @Nullable @Size(max = 200) String email) {}
