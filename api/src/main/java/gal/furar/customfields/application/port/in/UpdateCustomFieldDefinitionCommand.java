// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.DefinitionSpec;

public record UpdateCustomFieldDefinitionCommand(
    CustomFieldDefinitionId id, long expectedVersion, DefinitionSpec spec) {}
