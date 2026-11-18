// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.shared.domain.CustomFieldTarget;
import java.util.List;

/** Definicións para pintar formularios e para o editor de Axustes. */
public interface ListCustomFieldDefinitionsUseCase {

  /**
   * Activas, usadas no destino e visibles alí para quen pregunta (anónimo: só usos {@code PUBLIC}).
   */
  List<CustomFieldDefinition> listVisible(CustomFieldTarget target);

  /** Todas as usadas no destino, tamén as desactivadas, para o editor do xestor. */
  List<CustomFieldDefinition> listAll(CustomFieldTarget target);

  /** Todas as do concello, usadas onde sexa, para o editor do xestor. */
  List<CustomFieldDefinition> listAll();
}
