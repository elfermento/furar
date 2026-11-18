// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Os ocos libres para a portada común, sen sesión (ADR-0030): os mesmos de {@link
 * ReadAvailabilityUseCase#ofPage} para as instalacións dos concellos cos datos legais completos,
 * por páxinas de concellos coa mesma orde ca o catálogo de instalacións.
 */
public interface ReadCatalogAvailabilityUseCase {

  /** Concellos por páxina se non se pide outra cousa. */
  int DEFAULT_PAGE_SIZE = 20;

  /**
   * Unha páxina de concellos (empeza en 0). Sen data, hoxe en Europe/Madrid. Con {@code sports}
   * (claves de deporte), só contan os ocos dos usos que teñen algún deles.
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.invalid-parameter} se a páxina ou
   *     o tamaño non valen
   */
  CatalogAvailability ofCatalog(@Nullable LocalDate date, int page, int size, Set<String> sports);

  /** {@link #ofCatalog} sen filtro de deportes. */
  default CatalogAvailability ofCatalog(@Nullable LocalDate date, int page, int size) {
    return ofCatalog(date, page, size, Set.of());
  }
}
