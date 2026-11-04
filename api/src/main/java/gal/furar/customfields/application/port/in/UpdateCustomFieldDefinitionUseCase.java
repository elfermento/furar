// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;

/** O xestor cambia etiqueta, axuda, regras, opcións, dato persoal ou onde se usa un campo. */
public interface UpdateCustomFieldDefinitionUseCase {

  CustomFieldDefinition update(UpdateCustomFieldDefinitionCommand command);
}
