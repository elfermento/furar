// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Un anaco aberto da instalación nun día, xa sen as horas dos bloqueos: os seus instantes reais
 * ({@code [start, end)}) e o minuto de reloxo de parede, en Europe/Madrid, onde empeza o tramo do
 * horario do que sae, a orixe da grella dos ocos (ADR-0025, punto 6).
 */
public record OpenInterval(LocalDate date, Instant start, Instant end, int alignFromMinute) {

  public OpenInterval {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("Empty interval: " + start + " - " + end);
    }
    if (alignFromMinute < 0 || alignFromMinute >= 1440) {
      throw new IllegalArgumentException("Grid origin out of the day: " + alignFromMinute);
    }
  }
}
