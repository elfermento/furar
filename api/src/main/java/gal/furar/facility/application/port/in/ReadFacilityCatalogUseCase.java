// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.shared.domain.Language;

/**
 * As instalacións de todos os concellos para a portada común, sen sesión (ADR-0030). Un concello
 * sae só se ten os datos legais completos e polo menos unha instalación activa que vexa a
 * veciñanza; non hai botón nin estado de «publicar o concello».
 */
public interface ReadFacilityCatalogUseCase {

  /** Concellos por páxina se non se pide outra cousa. */
  int DEFAULT_PAGE_SIZE = 20;

  /**
   * Unha páxina de concellos, por nome (a páxina empeza en 0).
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.invalid-parameter} se a páxina ou
   *     o tamaño non valen
   */
  FacilityCatalog catalog(Language language, int page, int size);
}
