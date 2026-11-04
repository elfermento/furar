// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8b.adapter.out.persistence;

import gal.furar.testsupport.AbstractTenantIT;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** QA Q11: estende o contrato, pero visibleTo non chama á clase; A8b ten que rexeitalo. */
class SelectListSqlTenantIT extends AbstractTenantIT {
  SelectListSql subject;

  @Override
  protected Object seed(UUID municipality) {
    return municipality;
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return List.of(municipality);
  }
}
