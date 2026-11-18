// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A entidade titular dunha reserva (ADR-0039) e, nunha sesión, a súa tempada.
 *
 * @param seriesId nunha sesión, o id da súa tempada (unha sesión non se move, só se cancela)
 * @param series a tempada da sesión; nula nunha reserva extra ou se quen mira non a pode ver
 */
public record BookingOrganization(
    UUID id, String name, @Nullable UUID seriesId, @Nullable Season series) {

  public BookingOrganization {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
  }

  /**
   * A tempada dunha sesión.
   *
   * @param weekdays días da semana ISO (luns = 1)
   * @param sessions sesións confirmadas
   * @param paymentStatus o pago da tempada ({@code FREE}, {@code PENDING} ou {@code PAID})
   */
  public record Season(
      UUID id,
      List<Integer> weekdays,
      int startMinute,
      int endMinute,
      LocalDate firstDate,
      LocalDate lastDate,
      int sessions,
      String paymentStatus) {

    public Season {
      Objects.requireNonNull(id, "id");
      weekdays = List.copyOf(weekdays);
    }
  }
}
