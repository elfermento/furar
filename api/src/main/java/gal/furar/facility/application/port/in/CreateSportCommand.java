// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import org.jspecify.annotations.Nullable;

/** Deporte novo do concello («+ crear»): nome en gl e es (P26). */
public record CreateSportCommand(@Nullable String gl, @Nullable String es) {}
