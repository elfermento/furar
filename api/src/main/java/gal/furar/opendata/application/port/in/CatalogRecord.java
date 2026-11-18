// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import gal.furar.opendata.domain.Dataset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * O que precisa a ficha de catálogo DCAT-AP-ES (ADR-0034, punto 19).
 *
 * @param slug o concello, ou nulo na de todos
 * @param publisherName quen publica: o concello ou quen opera a instalación
 * @param dir3Code o seu código DIR3
 * @param issued desde cando publica
 * @param updatedOn o último cambio de cada conxunto
 */
public record CatalogRecord(
    @Nullable String slug,
    String publisherName,
    String dir3Code,
    Instant issued,
    Map<Dataset, LocalDate> updatedOn,
    LocalDate availableFrom,
    LocalDate availableTo) {

  public CatalogRecord {
    updatedOn = Map.copyOf(updatedOn);
  }
}
