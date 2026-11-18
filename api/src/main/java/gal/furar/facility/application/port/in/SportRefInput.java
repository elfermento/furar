// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Un deporte da tarxeta Datos: {@code code} do catálogo ou {@code customSportId}, só un. */
public record SportRefInput(@Nullable String code, @Nullable UUID customSportId) {}
