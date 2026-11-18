// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import java.util.List;
import java.util.Map;

/** Para o xestor: que campos e opcións teñen valores (dúas consultas para toda a lista). */
public interface GetCustomFieldInUseUseCase {

  Map<CustomFieldDefinitionId, CustomFieldInUse> inUse(List<CustomFieldDefinition> definitions);
}
