// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;

/** Listado de xestión, en orde alfabética no idioma de quen mira (ADR-0022, punto 10). */
public interface ListFacilitiesUseCase {

  PageResult<FacilityView> list(int page, int size, Language language);
}
