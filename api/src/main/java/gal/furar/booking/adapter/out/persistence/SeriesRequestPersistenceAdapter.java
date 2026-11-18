// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.SeriesRequests;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Solicitudes de tempada no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class SeriesRequestPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements SeriesRequests {

  private final SeriesRequestSql sql;

  SeriesRequestPersistenceAdapter(SeriesRequestSql sql) {
    this.sql = sql;
  }

  @Override
  public Optional<SeriesRequest> find(UUID id) {
    return sql.find(tenant().id(), id);
  }

  @Override
  public Optional<SeriesRequest> findForUpdate(UUID id) {
    return sql.findForUpdate(tenant().id(), id);
  }

  @Override
  public List<SeriesRequest> list(SeriesRequest.Status status) {
    return sql.list(tenant().id(), status.name());
  }

  @Override
  public int countPending() {
    return sql.countPending(tenant().id());
  }

  @Override
  public int countPending(UUID organizationId) {
    return sql.countPending(tenant().id(), organizationId);
  }

  @Override
  public List<SeriesRequest> ofOrganization(UUID organizationId) {
    return sql.ofOrganization(tenant().id(), organizationId);
  }

  @Override
  public void insert(SeriesRequest request) {
    sql.insert(tenant().id(), request);
  }

  @Override
  public void update(SeriesRequest before, SeriesRequest after) {
    if (!sql.update(tenant().id(), after, Objects.requireNonNull(before.version(), "version"))) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
  }

  @Override
  public void delete(UUID id) {
    sql.delete(tenant().id(), id);
  }

  @Override
  public int deletePending(UUID organizationId) {
    return sql.deletePending(tenant().id(), organizationId);
  }

  @Override
  public int purgeDecidedBefore(Instant cutoff, int limit) {
    return sql.purgeDecidedBefore(tenant().id(), cutoff, limit);
  }
}
