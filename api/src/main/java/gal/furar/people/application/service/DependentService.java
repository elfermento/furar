// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.DependentUsage;
import gal.furar.people.NoDependentUsage;
import gal.furar.people.application.port.in.ManageDependentsUseCase;
import gal.furar.people.application.port.in.SaveDependentCommand;
import gal.furar.people.application.port.out.DependentRepository;
import gal.furar.people.domain.Dependent;
import gal.furar.people.domain.DependentId;
import gal.furar.people.domain.PeopleError;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As persoas a cargo da conta da sesión (ADR-0031, puntos 1 e 6). Cada método filtra polo {@code
 * sub} do token: a ficha doutra conta ou desactivada non existe (404).
 *
 * <p>O límite de {@value Dependent#MAX_ACTIVE} fichas e a duplicada compróbanse co bloqueo das
 * fichas activas da conta; o índice único é a última garantía. Eliminar bloquea primeiro a ficha e
 * pregunta despois a {@link DependentUsage}: unha reserva que a use nese momento ou xa confirmou (a
 * ficha desactívase) ou espera e atopa a ficha borrada.
 */
@Service
public class DependentService implements ManageDependentsUseCase {

  private final DependentRepository dependents;
  private final DependentUsage usage;
  private final CurrentActor currentActor;
  private final Clock clock;

  public DependentService(
      DependentRepository dependents,
      ObjectProvider<DependentUsage> usage,
      CurrentActor currentActor,
      Clock clock) {
    this.dependents = dependents;
    // Un só DependentUsage (ADR-0005): sen ningún, NoDependentUsage.
    this.usage = usage.getIfAvailable(NoDependentUsage::new);
    this.currentActor = currentActor;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<Dependent> list() {
    return dependents.active(subject());
  }

  @Override
  @Transactional
  @PersonUseCase
  public Dependent create(SaveDependentCommand command) {
    String subject = subject();
    Dependent created =
        Dependent.create(
            DependentId.newId(),
            subject,
            command.name(),
            command.birthYear(),
            command.inMyCare(),
            clock.instant());
    List<Dependent> active = dependents.lockActive(subject);
    if (active.size() >= Dependent.MAX_ACTIVE) {
      throw new DomainException(PeopleError.DEPENDENT_LIMIT, Map.of("max", Dependent.MAX_ACTIVE));
    }
    checkDuplicate(active, created);
    dependents.insert(created);
    return saved(subject, created.id());
  }

  @Override
  @Transactional
  @PersonUseCase
  public Dependent update(DependentId id, long expectedVersion, SaveDependentCommand command) {
    String subject = subject();
    List<Dependent> active = dependents.lockActive(subject);
    Dependent current =
        active.stream()
            .filter(d -> d.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new DomainException(PeopleError.DEPENDENT_NOT_FOUND));
    if (!Objects.equals(current.version(), expectedVersion)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Dependent changed =
        current.update(command.name(), command.birthYear(), command.inMyCare(), clock.instant());
    checkDuplicate(active, changed);
    dependents.update(changed);
    return saved(subject, id);
  }

  @Override
  @Transactional
  @PersonUseCase
  public void delete(DependentId id) {
    String subject = subject();
    Dependent current =
        dependents
            .lock(subject, id)
            .filter(Dependent::isActive)
            .orElseThrow(() -> new DomainException(PeopleError.DEPENDENT_NOT_FOUND));
    Instant now = clock.instant();
    if (usage.isUsed(id.value())) {
      dependents.update(current.deactivate(now));
    } else {
      dependents.delete(subject, id);
    }
  }

  private static void checkDuplicate(List<Dependent> active, Dependent candidate) {
    boolean duplicate =
        active.stream()
            .anyMatch(
                d ->
                    !d.id().equals(candidate.id())
                        && d.birthYear() == candidate.birthYear()
                        && d.name().equalsIgnoreCase(candidate.name()));
    if (duplicate) {
      throw new DomainException(PeopleError.DEPENDENT_DUPLICATE);
    }
  }

  private Dependent saved(String subject, DependentId id) {
    return dependents.active(subject, List.of(id.value())).stream().findFirst().orElseThrow();
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }
}
