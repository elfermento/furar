// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import org.jspecify.annotations.Nullable;

/** Contacto do servizo de deportes enteiro; baleiro = sen ese dato. */
public record UpdateSportsContactCommand(
    long expectedVersion,
    @Nullable String name,
    @Nullable String address,
    @Nullable String phone,
    @Nullable String email) {}
