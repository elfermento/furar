// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;

/** O xestor define un campo novo no seu concello. */
public interface CreateCustomFieldDefinitionUseCase {

  CustomFieldDefinition create(CreateCustomFieldDefinitionCommand command);
}
