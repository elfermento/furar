// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import gal.furar.facility.PublicCustomFields;
import gal.furar.opendata.domain.Dataset;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * O resumo da páxina «Datos abertos» (ADR-0034, punto 10).
 *
 * @param updatedOn o día do último cambio de cada conxunto que xa ten (ningún antes da primeira
 *     noite)
 * @param maxMonths meses como moito nunha descarga da ocupación (12 nun concello, 1 en todos)
 * @param csvBytesPerDay tamaño medio dun día da ocupación en CSV (0 sen datos)
 * @param jsonBytesPerDay o mesmo en JSON
 * @param catalog se hai ficha de catálogo
 * @param municipalities cantos concellos entran
 * @param legalEntityName a entidade que se cita (só nun concello, se a ten)
 * @param customFields os campos personalizados públicos das instalacións (só nun concello)
 */
public record OpenDataOverview(
    Map<Dataset, LocalDate> updatedOn,
    LocalDate availableFrom,
    LocalDate availableTo,
    LocalDate defaultFrom,
    LocalDate defaultTo,
    int maxMonths,
    long csvBytesPerDay,
    long jsonBytesPerDay,
    boolean catalog,
    int municipalities,
    @Nullable String legalEntityName,
    List<PublicCustomFields.Definition> customFields) {

  public OpenDataOverview {
    updatedOn = Map.copyOf(updatedOn);
    customFields = List.copyOf(customFields);
  }
}
