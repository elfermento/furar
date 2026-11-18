// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.in;

import gal.furar.people.domain.Dependent;
import gal.furar.people.domain.DependentId;
import gal.furar.shared.domain.DomainException;
import java.util.List;

/**
 * As persoas a cargo da conta da sesión (ADR-0031, puntos 1, 6 e 9). Da persoa e sen concello:
 * sesión e correo verificado, e sempre filtrado polo seu {@code sub}; a ficha doutra conta ou
 * desactivada non existe para ela.
 */
public interface ManageDependentsUseCase {

  /** As fichas activas, por nome. */
  List<Dependent> list();

  /**
   * @throws DomainException {@code people.dependent-limit}, {@code people.dependent-duplicate},
   *     {@code people.dependent-declaration-required} ou {@code
   *     people.dependent-birth-year-invalid}
   */
  Dependent create(SaveDependentCommand command);

  /**
   * @param expectedVersion a do {@code If-Match}
   * @throws DomainException {@code people.dependent-not-found}, {@code common.stale-version} ou os
   *     de {@link #create}
   */
  Dependent update(DependentId id, long expectedVersion, SaveDependentCommand command);

  /**
   * Elimina a ficha: bórraa se nunca se usou nunha reserva e, se se usou, desactívaa. Para a persoa
   * é o mesmo.
   *
   * @throws DomainException {@code people.dependent-not-found}
   */
  void delete(DependentId id);
}
