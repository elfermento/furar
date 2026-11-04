// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8bok.adapter.out.persistence;

import gal.furar.testsupport.AbstractTenantIT;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Forma mínima que A8b acepta: estende {@link AbstractTenantIT} e o seu {@code visibleTo} chama a
 * {@link ScopedSql}. É un fixture: failsafe exclúe {@code archviolations} e non o executa.
 */
class ScopedSqlTenantIT extends AbstractTenantIT {
  ScopedSql subject;

  @Override
  protected Object seed(UUID municipality) {
    return insertField(municipality, "code", "PUBLIC");
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return List.of(subject.count(municipality));
  }
}
