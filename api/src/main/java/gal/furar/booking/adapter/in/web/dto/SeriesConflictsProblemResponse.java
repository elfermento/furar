// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.Map;

/**
 * Problema RFC 9457 {@code booking.series-conflicts-changed} (409) coa extensión {@code preview}: a
 * vista previa calculada baixo o bloqueo (ADR-0039, punto 8). Só para o contrato: escríbeo {@code
 * BookingProblemHandler}.
 */
public record SeriesConflictsProblemResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    Map<String, Object> params,
    String traceId,
    SeriesPreviewResponse preview) {}
