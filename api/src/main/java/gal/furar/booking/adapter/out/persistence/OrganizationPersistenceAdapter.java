// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.OrganizationStatus;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Entidades no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class OrganizationPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements Organizations {

  private final OrganizationSql sql;

  OrganizationPersistenceAdapter(OrganizationSql sql) {
    this.sql = sql;
  }

  @Override
  public Optional<Organization> find(UUID id) {
    UUID m = tenant().id();
    return sql.find(m, id).map(h -> withManagers(m, h));
  }

  @Override
  public Optional<Organization> findForUpdate(UUID id) {
    UUID m = tenant().id();
    return sql.findForUpdate(m, id).map(h -> withManagers(m, h));
  }

  @Override
  public Optional<Organization> findForShare(UUID id) {
    UUID m = tenant().id();
    return sql.findForShare(m, id).map(h -> withManagers(m, h));
  }

  @Override
  public List<Row> list(boolean archived, Instant now) {
    UUID m = tenant().id();
    List<OrganizationSql.Listed> rows = sql.list(m, archived, now);
    Map<UUID, List<Organization.Manager>> managers =
        managers(m, rows.stream().map(r -> r.head().id()).toList());
    return rows.stream()
        .map(
            r ->
                new Row(
                    organization(r.head(), managers.getOrDefault(r.head().id(), List.of())),
                    r.managers(),
                    OrganizationStatus.of(r.pending(), r.future())))
        .toList();
  }

  @Override
  public void insert(Organization organization) {
    UUID m = tenant().id();
    try {
      sql.insert(m, organization);
      organization.managers().forEach(x -> sql.insertManager(m, organization.id(), x));
    } catch (DataIntegrityViolationException e) {
      throw refused(e);
    }
  }

  @Override
  public void update(Organization before, Organization after) {
    UUID m = tenant().id();
    try {
      if (!sql.update(m, after, Objects.requireNonNull(before.version(), "version"))) {
        throw new DomainException(CommonError.STALE_VERSION);
      }
      if (!before.managers().equals(after.managers())) {
        sql.deleteManagers(m, after.id());
        after.managers().forEach(x -> sql.insertManager(m, after.id(), x));
      }
    } catch (DataIntegrityViolationException e) {
      throw refused(e);
    }
  }

  @Override
  public void delete(UUID id) {
    sql.delete(tenant().id(), id);
  }

  @Override
  public boolean hasHistory(UUID id) {
    UUID m = tenant().id();
    return sql.hasBookings(m, id) || sql.hasSeries(m, id);
  }

  @Override
  public boolean hasFutureBookings(UUID id, Instant now) {
    return sql.hasFutureBookings(tenant().id(), id, now);
  }

  @Override
  public List<Mine> ofManager(String emailKey) {
    UUID m = tenant().id();
    List<UUID> ids = sql.ofManager(m, emailKey);
    Map<UUID, String> names = names(ids);
    return ids.stream().map(id -> new Mine(id, names.get(id))).toList();
  }

  @Override
  public void renameBookings(UUID id, String name) {
    sql.renameBookings(tenant().id(), id, name);
  }

  @Override
  public Map<UUID, String> names(Collection<UUID> ids) {
    return sql.byIds(tenant().id(), ids).stream()
        .collect(Collectors.toMap(OrganizationSql.Head::id, OrganizationSql.Head::name));
  }

  private Organization withManagers(UUID m, OrganizationSql.Head head) {
    return organization(head, managers(m, List.of(head.id())).getOrDefault(head.id(), List.of()));
  }

  private Map<UUID, List<Organization.Manager>> managers(UUID m, List<UUID> ids) {
    return sql.managers(m, ids).stream()
        .collect(
            Collectors.groupingBy(
                OrganizationSql.ManagerRow::organizationId,
                Collectors.mapping(OrganizationSql.ManagerRow::manager, Collectors.toList())));
  }

  private static Organization organization(
      OrganizationSql.Head h, List<Organization.Manager> managers) {
    return Organization.rehydrate(
        h.id(),
        h.name(),
        h.contactName(),
        h.email(),
        h.phone(),
        managers,
        h.archivedAt(),
        h.version(),
        h.createdAt(),
        h.updatedAt());
  }

  private static RuntimeException refused(DataIntegrityViolationException e) {
    String message = String.valueOf(e.getMostSpecificCause().getMessage());
    if (message.contains("booking_organization_name_uk")) {
      return new DomainException(BookingError.ORGANIZATION_NAME_TAKEN);
    }
    return e;
  }
}
