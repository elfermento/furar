// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.out;

import gal.furar.people.domain.Dependent;
import gal.furar.people.domain.DependentId;
import gal.furar.shared.domain.DomainException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * As fichas gardadas (ADR-0031, punto 2). Sen concello: cada lectura e escritura vai filtrada polo
 * {@code sub} da conta, agás a tarefa de conservación.
 */
public interface DependentRepository {

  /** As activas da conta, por nome. */
  List<Dependent> active(String subject);

  /** As activas da conta con estes ids. */
  List<Dependent> active(String subject, Collection<UUID> ids);

  /**
   * Bloquea as fichas activas da conta ({@code for update}) e devólveas: o límite e a duplicada
   * compróbanse sen carreiras entre dúas peticións da mesma conta.
   */
  List<Dependent> lockActive(String subject);

  /** A ficha da conta, activa ou non, bloqueada ({@code for update}). */
  Optional<Dependent> lock(String subject, DependentId id);

  /**
   * @throws DomainException {@code people.dependent-duplicate} se o para o índice único
   */
  void insert(Dependent dependent);

  /**
   * Garda os cambios (nome, ano, declaración ou desactivación) se a versión coincide.
   *
   * @throws DomainException {@code common.stale-version} ou {@code people.dependent-duplicate}
   */
  void update(Dependent dependent);

  /** Borra a ficha da conta. */
  void delete(String subject, DependentId id);

  /** As desactivadas de todas as contas, para a conservación. */
  List<UUID> deactivated();

  /** Borra unha ficha desactivada (conservación). */
  void purge(UUID id);
}
