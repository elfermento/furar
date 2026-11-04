// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.shared.domain.CustomFieldTarget;
import java.util.Map;

/** JSON Schema 2020-12 dos campos visibles para quen pregunta. */
public interface GetCustomFieldSchemaUseCase {

  Map<String, Object> schema(CustomFieldTarget target);
}
