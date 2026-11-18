// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;

/** Unha definición, para editala. */
public interface GetCustomFieldDefinitionUseCase {

  CustomFieldDefinition get(CustomFieldDefinitionId id);
}
