// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;
import java.util.List;

/** Todas as definicións do concello (activas e desactivadas) para a portabilidade (ADR-0018). */
public interface ExportCustomFieldDefinitionsUseCase {

  List<CustomFieldDefinition> exportAll();
}
