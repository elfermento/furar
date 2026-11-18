// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.out;

import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.Slug;
import gal.furar.shared.domain.PageResult;
import java.util.Optional;

public interface MunicipalityRepository {

  Optional<Municipality> findById(MunicipalityId id);

  boolean existsBySlug(Slug slug);

  PageResult<Municipality> findPage(int page, int size);

  /** Garda un concello novo; slug repetido → {@code municipality.slug-taken}. */
  Municipality insert(Municipality municipality);

  /** Garda os cambios; versión distinta → {@code common.stale-version}. */
  Municipality update(Municipality municipality);
}
