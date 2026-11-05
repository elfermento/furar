// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import java.util.List;

/**
 * API pública: os campos dun destino que pode ver quen le, como columnas dunha descarga (CSV),
 * nunha soa lectura das definicións do concello actual (ARQ-6). Son os mesmos que deixa {@link
 * CustomFieldProjector}, na mesma orde.
 */
public interface CustomFieldColumns {

  List<CustomFieldColumn> columns(CustomFieldTarget target, Audience viewer);
}
