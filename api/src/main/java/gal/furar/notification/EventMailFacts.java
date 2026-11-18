// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que contan os correos dun evento (ADR-0038): o nome e o lugar en gl e es (cada persoa recíbeo
 * no seu idioma) e cando empeza.
 *
 * @param eventId o evento: o destino no rexistro de envíos e a ligazón á súa ficha
 * @param nameGl nome en galego
 * @param nameEs nome en castelán
 * @param placeGl lugar en galego (a instalación ou o texto de «Outro sitio»)
 * @param placeEs lugar en castelán
 * @param startsAt cando empeza (nunha actividade, a primeira sesión)
 * @param schedule o horario dunha actividade (ADR-0041); nulo nun evento dun día
 */
public record EventMailFacts(
    UUID eventId,
    String nameGl,
    String nameEs,
    String placeGl,
    String placeEs,
    Instant startsAt,
    @Nullable Schedule schedule) {

  /** Un evento dun día (ADR-0038). */
  public EventMailFacts(
      UUID eventId,
      String nameGl,
      String nameEs,
      String placeGl,
      String placeEs,
      Instant startsAt) {
    this(eventId, nameGl, nameEs, placeGl, placeEs, startsAt, null);
  }

  /**
   * O horario dunha actividade: días da semana ISO (luns = 1), horas de reloxo de parede en minutos
   * e datas.
   */
  public record Schedule(
      List<Integer> weekdays,
      int startMinute,
      int endMinute,
      LocalDate firstDate,
      LocalDate lastDate) {

    public Schedule {
      weekdays = List.copyOf(weekdays);
      Objects.requireNonNull(firstDate, "firstDate");
      Objects.requireNonNull(lastDate, "lastDate");
    }
  }

  public EventMailFacts {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(nameGl, "nameGl");
    Objects.requireNonNull(nameEs, "nameEs");
    Objects.requireNonNull(placeGl, "placeGl");
    Objects.requireNonNull(placeEs, "placeEs");
    Objects.requireNonNull(startsAt, "startsAt");
  }

  public String name(Language language) {
    return language == Language.ES ? nameEs : nameGl;
  }

  public String place(Language language) {
    return language == Language.ES ? placeEs : placeGl;
  }
}
