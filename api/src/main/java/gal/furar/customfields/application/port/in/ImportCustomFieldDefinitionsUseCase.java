// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import java.util.List;

/** Carga as definicións dun ficheiro {@code furar-export} no concello novo (ADR-0018). */
public interface ImportCustomFieldDefinitionsUseCase {

  void importAll(List<RestoreCustomFieldDefinitionCommand> definitions);
}
