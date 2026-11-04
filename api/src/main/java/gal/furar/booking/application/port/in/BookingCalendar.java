// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O calendario de xestión dun período (maqueta 27): só as reservas confirmadas (as canceladas non
 * ocupan). O día e a semana dunha instalación levan as reservas; a semana de todas, as contas por
 * día e hora; o mes, as contas por día.
 *
 * @param from primeiro día do período
 * @param to último día do período (incluído)
 * @param blocks as reservas do período, por día e hora (día; semana dunha instalación)
 * @param days reservas que empezan cada día (mes)
 * @param hours reservas que empezan cada día e hora (semana de todas as instalacións)
 * @param openings os tramos abertos de cada instalación e día (día e semana; o mes, baleiro): o
 *     resto das horas píntase pechado
 */
public record BookingCalendar(
    CalendarView view,
    LocalDate from,
    LocalDate to,
    List<ManagedBooking> blocks,
    List<Count> days,
    List<Count> hours,
    List<Opening> openings) {

  public BookingCalendar {
    Objects.requireNonNull(view, "view");
    blocks = List.copyOf(blocks);
    days = List.copyOf(days);
    hours = List.copyOf(hours);
    openings = List.copyOf(openings);
  }

  /**
   * Cantas reservas empezan nese día (e nesa hora, en Europe/Madrid).
   *
   * @param hour hora de 0 a 23, só nas contas por hora
   */
  public record Count(LocalDate date, @Nullable Integer hour, long count) {}

  /**
   * Un tramo aberto dunha instalación nun día, en minutos de reloxo de Europe/Madrid (0–1440;
   * inicio incluído e fin excluído).
   */
  public record Opening(UUID facilityId, LocalDate date, int startMinute, int endMinute) {}
}
