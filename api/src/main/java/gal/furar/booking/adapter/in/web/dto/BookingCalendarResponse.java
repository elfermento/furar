// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * O calendario de xestión dun período (maqueta 27): só as reservas confirmadas. Día e semana dunha
 * instalación: {@code blocks}. Semana de todas: {@code hours}. Mes: {@code days}. O resto, baleiro.
 * Día e semana levan tamén os tramos abertos ({@code openings}) da instalación ou de todas.
 *
 * @param from primeiro día do período
 * @param to último día do período (incluído)
 */
public record BookingCalendarResponse(
    CalendarViewValue view,
    LocalDate from,
    LocalDate to,
    List<ManagedBookingResponse> blocks,
    List<BookingDayCountResponse> days,
    List<BookingHourCountResponse> hours,
    List<BookingOpeningResponse> openings) {}
