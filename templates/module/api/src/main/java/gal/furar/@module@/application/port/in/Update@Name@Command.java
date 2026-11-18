// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import gal.furar.@module@.domain.@Name@Id;
import java.util.Map; // @cf

/** Edición con control de concorrencia: {@code expectedVersion} sae do {@code If-Match}. */
public record Update@Name@Command(
    @Name@Id id,
    long expectedVersion,
    Map<String, Object> customFields, // @cf
    String name) {}
