// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import org.jspecify.annotations.Nullable;

/** Descrición en gl e es; os dous baleiros = sen descrición. */
public record DescriptionInput(@Nullable String gl, @Nullable String es) {}
