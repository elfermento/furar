// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.CustomFieldTarget;
import java.util.Objects;

/**
 * Onde se usa un campo (especificación, bloque 1b): o destino co seu obrigatorio e a súa
 * visibilidade. Un mesmo campo pode ser obrigatorio e público nun sitio e opcional e interno
 * noutro.
 */
public record FieldUsage(CustomFieldTarget target, boolean required, Visibility visibility) {

  public FieldUsage {
    Objects.requireNonNull(target, "target");
    visibility = visibility == null ? Visibility.STAFF : visibility;
  }
}
