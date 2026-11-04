// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un tramo aberto dunha instalación nun día (maqueta 27: o resto das horas, pechadas e raiadas).
 *
 * @param startMinute minuto de reloxo de Europe/Madrid no que abre (0–1440, incluído)
 * @param endMinute minuto no que pecha (excluído)
 */
public record BookingOpeningResponse(
    UUID facilityId, LocalDate date, int startMinute, int endMinute) {}
