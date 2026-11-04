// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.domain.PageResult;

/** Lista pública dos concellos da instalación. */
public interface ListMunicipalitiesUseCase {

  PageResult<Municipality> list(int page, int size);
}
