// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.in.web.dto;

import java.time.Instant;
import java.util.Map; // @cf
import java.util.UUID;

/** {@code customFields}: só os campos que pode ver quen pregunta. */
public record @Name@Response(
    UUID id,
    String name,
    Map<String, Object> customFields, // @cf
    long version,
    Instant createdAt,
    Instant updatedAt) {}
