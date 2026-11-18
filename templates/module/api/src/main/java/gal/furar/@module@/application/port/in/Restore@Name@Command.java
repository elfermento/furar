// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import java.time.Instant;
import java.util.Map; // @cf
import java.util.UUID;
import org.jspecify.annotations.Nullable; // @cf

/** Fila da sección @module@ dun ficheiro furar-export: co seu id e as súas datas. */
public record Restore@Name@Command(
    UUID id,
    String name,
    @Nullable Map<String, Object> customFields, // @cf
    Instant createdAt,
    Instant updatedAt) {}
