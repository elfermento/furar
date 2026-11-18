// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.List;
import java.util.Objects;

/**
 * As partidas abertas da portada común (ADR-0040, punto 10; ADR-0030), sen concello no URL nin
 * sesión: por páxinas de concellos, coma {@code /catalog/availability}. Só os concellos da portada
 * común coa función acesa e algunha partida.
 */
public interface ReadCatalogOpenMatchesUseCase {

  /** Concellos por páxina se non se pide outro tamaño. */
  int DEFAULT_PAGE_SIZE = 20;

  /** Partidas como moito por concello. */
  int MAX_PER_MUNICIPALITY = 20;

  CatalogOpenMatches ofCatalog(int page, int size);

  /**
   * Unha páxina de concellos.
   *
   * @param hasNext se quedan concellos despois desta páxina
   */
  record CatalogOpenMatches(
      int page, int size, boolean hasNext, List<Municipality> municipalities) {

    public CatalogOpenMatches {
      municipalities = List.copyOf(municipalities);
    }
  }

  /** Un concello coas súas partidas. */
  record Municipality(String slug, String name, List<OpenMatchView> items) {

    public Municipality {
      Objects.requireNonNull(slug, "slug");
      Objects.requireNonNull(name, "name");
      items = List.copyOf(items);
    }
  }
}
