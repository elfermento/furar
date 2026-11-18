// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Problema RFC 9457 {@code booking.slot-taken} (409) coa extensión {@code alternatives[]}: ata tres
 * ocos libres do mesmo uso e día, os máis próximos, nos que caben as mesmas franxas (ADR-0025,
 * punto 9). Só para o contrato: escríbeo {@code BookingProblemHandler}.
 */
public record SlotTakenProblemResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    Map<String, Object> params,
    String traceId,
    List<AvailableSlotResponse> alternatives) {}
