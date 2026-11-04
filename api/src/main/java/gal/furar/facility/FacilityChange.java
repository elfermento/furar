// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Cambio dunha instalación que pode deixar fóra reservas futuras (ADR-0022, punto 2). Selado: cada
 * tarefa que o usa (usos, horario, bloqueos) pode axustar os campos da súa variante.
 */
public sealed interface FacilityChange {

  UUID facilityId();

  /** Pechar temporalmente. */
  record Closure(UUID facilityId) implements FacilityChange {}

  /** Dar de baixa. */
  record Withdrawal(UUID facilityId) implements FacilityChange {}

  /**
   * Un uso que pasa a bloquear os demais nuns días (só os que engade: os que xa bloqueaba
   * decidíronse antes), como rangos ordenados cos dous extremos incluídos, e só nos seus días da
   * semana ({@code weekdays}, máscara luns = 1 … domingo = 64).
   */
  record ExclusiveUse(UUID facilityId, UUID useId, List<Dates> dates, int weekdays)
      implements FacilityChange {

    public ExclusiveUse {
      dates = List.copyOf(dates);
    }

    /** Todos os días da semana. */
    public ExclusiveUse(UUID facilityId, UUID useId, List<Dates> dates) {
      this(facilityId, useId, dates, 127);
    }
  }

  /** Rango de datas cos dous extremos incluídos. */
  record Dates(LocalDate from, LocalDate to) {}

  /**
   * Bloqueo novo ou cambio do seu período (instantes reais: o servidor xa converteu as horas de
   * Europe/Madrid).
   */
  record Block(UUID facilityId, @Nullable UUID blockId, Instant start, Instant end)
      implements FacilityChange {}

  /** Cambio dun uso que non deixa as súas reservas como estaban. */
  record UseChanged(UUID facilityId, UUID useId) implements FacilityChange {}

  /**
   * As prazas dun uso por prazas pasan a ser {@code capacity}. Non deixa reservas fóra: baixar con
   * reservas futuras xa o rexeita {@link UseChanged} (P1). Só se aplica, sen decisións, despois de
   * gardar o uso: {@code booking} leva a nova capacidade aos seus contadores das franxas que aínda
   * non empezaron (ADR-0012).
   */
  record SeatsChanged(UUID facilityId, UUID useId, int capacity) implements FacilityChange {}

  /** Arquivar un uso. */
  record UseArchived(UUID facilityId, UUID useId) implements FacilityChange {}

  /**
   * Cambio de tempada, semana ou excepción que deixa horas fóra do horario (P6). Leva os tramos
   * exactos que estaban abertos e deixan de estalo (en orde, cos instantes reais), calculados
   * comparando o horario de antes e o de despois: unha reserva está afectada se pisa algún. Se non
   * se perde ningún minuto, non hai cambio que preguntar.
   */
  record ScheduleChanged(UUID facilityId, List<OpeningInterval> lost) implements FacilityChange {

    public ScheduleChanged {
      lost = List.copyOf(lost);
    }
  }
}
