// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import java.util.UUID;

/**
 * {@link DependentUsage} sen o módulo {@code booking}: ningunha ficha ten reservas. Non é un bean:
 * úsase cando non hai ningún (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoDependentUsage implements DependentUsage {

  @Override
  public boolean isUsed(UUID dependentId) {
    return false;
  }
}
