// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.List;

/** API pública: na lectura, deixa só os campos que pode ver quen le, tipados pola definición. */
public interface CustomFieldProjector {

  /** Un rexistro (detalle). */
  CustomFieldValues project(CustomFieldTarget target, CustomFieldValues stored, Audience viewer);

  /**
   * Varios rexistros (listas, CSV): le as definicións unha soa vez, non unha por fila (ARQ-6).
   * Devolve os valores proxectados na mesma orde.
   */
  List<CustomFieldValues> projectAll(
      CustomFieldTarget target, List<CustomFieldValues> stored, Audience viewer);
}
