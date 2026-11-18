// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O horario dunha actividade do concello nunha instalación (ADR-0041), coma o dunha tempada
 * (ADR-0039, punto 8): uso, parte (só nos usos por partes), días da semana ISO (luns = 1), horas de
 * reloxo de parede en Europe/Madrid de 15 en 15 minutos ({@code 1440}: a medianoite do día
 * seguinte) e datas, as dúas incluídas.
 */
public record ActivitySlot(
    UUID facilityId,
    UUID useId,
    @Nullable UUID partId,
    List<Integer> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate) {

  public ActivitySlot {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(firstDate, "firstDate");
    Objects.requireNonNull(lastDate, "lastDate");
    weekdays = List.copyOf(weekdays);
  }
}
