// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * O que crearía a tempada (ADR-0039, punto 8): cantas sesións e cales días non se crean.
 *
 * @param sessions sesións que se crean
 * @param holidays festivos (teñan ou non horario propio)
 * @param useOff días nos que o uso non está vixente
 * @param closed días pechados (tamén a sesión enteira na hora que non existe o día do cambio)
 * @param conflicts días con choque: as súas datas son o {@code skip} de crear
 */
public record SeriesPreviewResponse(
    int sessions,
    @Nullable Instant firstSession,
    @Nullable Instant lastSession,
    List<LocalDate> holidays,
    List<LocalDate> useOff,
    List<LocalDate> closed,
    List<SeriesConflictResponse> conflicts) {}
