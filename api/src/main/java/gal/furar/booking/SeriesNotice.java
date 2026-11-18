// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha tempada tal como a conta o correo á entidade (ADR-0039, punto 12; A19: só tipos da raíz e de
 * Java): o correo e o idioma da entidade, os nomes de agora e as datas.
 *
 * @param language {@code gl} ou {@code es}: o primeiro idioma do concello
 * @param weekdays días da semana ISO (luns = 1)
 * @param sessions sesións confirmadas agora
 * @param skipped as datas da tempada que non se crearon
 * @param fromRequest se saíu dunha solicitude da entidade
 * @param endedFrom desde cando se rematou
 * @param cancelled sesións canceladas ao rematala
 */
public record SeriesNotice(
    UUID seriesId,
    String organizationName,
    String email,
    String language,
    String facilityName,
    @Nullable String facilityAddress,
    String useName,
    @Nullable String partName,
    List<Integer> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate,
    int sessions,
    List<LocalDate> skipped,
    boolean fromRequest,
    @Nullable LocalDate endedFrom,
    int cancelled) {

  public SeriesNotice {
    Objects.requireNonNull(seriesId, "seriesId");
    Objects.requireNonNull(organizationName, "organizationName");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    weekdays = List.copyOf(weekdays);
    skipped = List.copyOf(skipped);
  }
}
