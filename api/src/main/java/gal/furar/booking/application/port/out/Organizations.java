// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.OrganizationStatus;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * As entidades do concello e as súas persoas responsables (ADR-0039), sempre no concello actual e
 * dentro dunha transacción (ADR-0006). A orde de bloqueo é sempre instalación → entidade →
 * solicitude ou serie.
 */
public interface Organizations {

  /** A entidade, dada de alta ou arquivada, cos seus responsables. */
  Optional<Organization> find(UUID id);

  /** Coma {@link #find}, co bloqueo da fila para escribir ({@code for update}). */
  Optional<Organization> findForUpdate(UUID id);

  /**
   * Coma {@link #find}, co bloqueo compartido da fila ({@code for share}): mentres dura, ninguén a
   * arquiva nin a borra, e outras reservas da mesma entidade non esperan.
   */
  Optional<Organization> findForShare(UUID id);

  /** Unha fila da táboa: a entidade, cantos responsables ten e o seu estado. */
  record Row(Organization organization, int managers, OrganizationStatus status) {}

  /**
   * As entidades dadas de alta (ou só as arquivadas), por nome, co seu estado agora.
   *
   * @param archived as arquivadas en vez das dadas de alta
   */
  List<Row> list(boolean archived, Instant now);

  /**
   * Garda unha nova cos seus responsables.
   *
   * @throws DomainException {@code booking.organization-name-taken}
   */
  void insert(Organization organization);

  /**
   * Garda os cambios (versión + 1) e substitúe os responsables.
   *
   * @throws DomainException {@code common.stale-version}; {@code booking.organization-name-taken}
   */
  void update(Organization before, Organization after);

  /** Bórraa, cos seus responsables e solicitudes (en cascada). */
  void delete(UUID id);

  /** Se ten algunha reserva ou tempada na súa historia, en calquera estado. */
  boolean hasHistory(UUID id);

  /** Se ten sesións ou reservas confirmadas que aínda non remataron. */
  boolean hasFutureBookings(UUID id, Instant now);

  /** As entidades dadas de alta das que ese correo (normalizado) é responsable, por nome. */
  List<Mine> ofManager(String emailKey);

  /** Unha entidade da persoa responsable. */
  record Mine(UUID id, String name) {}

  /** O nome novo nas súas reservas sen anonimizar (o titular é unha copia). */
  void renameBookings(UUID id, String name);

  /** Os nomes das entidades con eses ids, tamén arquivadas. */
  Map<UUID, String> names(Collection<UUID> ids);
}
