// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * As solicitudes de tempada das entidades (ADR-0039, puntos 10 e 11), sempre no concello actual e
 * dentro dunha transacción (ADR-0006). A solicitude bloquéase sempre despois da instalación e da
 * entidade.
 */
public interface SeriesRequests {

  Optional<SeriesRequest> find(UUID id);

  /** Coa fila bloqueada ({@code for update}). */
  Optional<SeriesRequest> findForUpdate(UUID id);

  /** As do concello nese estado, da máis vella á máis nova. */
  List<SeriesRequest> list(SeriesRequest.Status status);

  /** Cantas pendentes hai no concello. */
  int countPending();

  /** Cantas pendentes ten a entidade. */
  int countPending(UUID organizationId);

  /** As da entidade, da máis nova á máis vella. */
  List<SeriesRequest> ofOrganization(UUID organizationId);

  void insert(SeriesRequest request);

  /**
   * Garda a decisión (versión + 1).
   *
   * @throws DomainException {@code common.stale-version}
   */
  void update(SeriesRequest before, SeriesRequest after);

  void delete(UUID id);

  /** Borra as pendentes da entidade (ao arquivala). */
  int deletePending(UUID organizationId);

  /** Borra ata {@code limit} solicitudes decididas antes de {@code cutoff} (a conservación). */
  int purgeDecidedBefore(Instant cutoff, int limit);
}
