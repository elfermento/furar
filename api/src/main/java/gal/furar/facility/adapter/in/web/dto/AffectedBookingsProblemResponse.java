// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Problema RFC 9457 {@code facility.affected-bookings} (409) coa extensión {@code affected[]}
 * (ADR-0008, ADR-0022 punto 6). Só para o contrato: escríbeo {@code FacilityProblemHandler}.
 */
public record AffectedBookingsProblemResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    Map<String, Object> params,
    String traceId,
    List<AffectedBookingResponse> affected) {}
