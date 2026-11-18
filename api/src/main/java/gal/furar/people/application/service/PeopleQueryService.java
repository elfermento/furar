// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.DependentSummary;
import gal.furar.people.PeopleQueries;
import gal.furar.people.application.port.out.AccountEmails;
import gal.furar.people.application.port.out.DependentRepository;
import gal.furar.people.domain.Dependent;
import gal.furar.shared.domain.EmailKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link PeopleQueries}) para outros módulos. Sen permisos
 * propios porque non é un caso de uso: quen chama xa os comprobou e dá o {@code sub} da conta.
 */
@Service
class PeopleQueryService implements PeopleQueries {

  private final DependentRepository dependents;
  private final AccountEmails accounts;

  PeopleQueryService(DependentRepository dependents, AccountEmails accounts) {
    this.dependents = dependents;
    this.accounts = accounts;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DependentSummary> dependents(String subject) {
    return dependents.active(subject).stream().map(PeopleQueryService::summary).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DependentSummary> dependents(String subject, Collection<UUID> ids) {
    return dependents.active(subject, ids).stream().map(PeopleQueryService::summary).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> accountByEmail(String email) {
    return accounts.subjectOf(EmailKey.of(email));
  }

  private static DependentSummary summary(Dependent dependent) {
    return new DependentSummary(dependent.id().value(), dependent.name(), dependent.birthYear());
  }
}
