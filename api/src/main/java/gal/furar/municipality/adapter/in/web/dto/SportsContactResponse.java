// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/** Contacto do servizo de deportes do concello; cada dato, se o hai. */
public record SportsContactResponse(
    @Nullable String name,
    @Nullable String address,
    @Nullable String phone,
    @Nullable String email) {}
