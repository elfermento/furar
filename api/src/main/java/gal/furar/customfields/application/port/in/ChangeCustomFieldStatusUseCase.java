// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;

/**
 * Desactivar (deixa de pedirse; os valores consérvanse e vense) ou activar un campo, e eliminalo se
 * aínda non ten valores (ADR-0011, revisión do 2026-11-04).
 */
public interface ChangeCustomFieldStatusUseCase {

  CustomFieldDefinition deactivate(CustomFieldDefinitionId id, long expectedVersion);

  CustomFieldDefinition activate(CustomFieldDefinitionId id, long expectedVersion);

  /**
   * Borra o campo, as súas opcións e o seu uso.
   *
   * @throws gal.furar.shared.domain.DomainException {@code customfield.has-values} (409) se ten
   *     algún valor gardado: desactívase
   */
  void delete(CustomFieldDefinitionId id, long expectedVersion);
}
