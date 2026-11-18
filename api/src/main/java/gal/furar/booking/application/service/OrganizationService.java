// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.in.ManageOrganizationsUseCase;
import gal.furar.booking.application.port.in.OrganizationCommand;
import gal.furar.booking.application.port.in.OrganizationRow;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.SeriesRequests;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.OrganizationStatus;
import gal.furar.shared.audit.AuditChanges;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.RequiresPermission;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As entidades do concello (ADR-0039, punto 7). Eliminar bloquea primeiro a fila da entidade e só
 * despois mira a súa historia: así non se cruza con quen reserva ou aproba no seu nome (que a
 * bloquean despois da instalación). Con historia non se borra: arquívase.
 */
@Service
public class OrganizationService implements ManageOrganizationsUseCase {

  static final String CREATE = "booking.organization-create";
  static final String UPDATE = "booking.organization-update";
  static final String ARCHIVE = "booking.organization-archive";
  static final String DELETE = "booking.organization-delete";

  private final Organizations organizations;
  private final SeriesRequests requests;
  private final SeasonSupport support;
  private final Clock clock;

  public OrganizationService(
      Organizations organizations, SeriesRequests requests, SeasonSupport support, Clock clock) {
    this.organizations = organizations;
    this.requests = requests;
    this.support = support;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public List<OrganizationRow> list(@Nullable OrganizationStatus status, boolean archived) {
    return organizations.list(archived, clock.instant()).stream()
        .filter(r -> status == null || r.status() == status)
        .map(r -> new OrganizationRow(r.organization(), r.managers(), r.status()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public Organization get(UUID id) {
    return organizations
        .find(id)
        .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public Organization create(OrganizationCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    return support.audited(
        CREATE,
        details,
        () -> {
          Instant now = clock.instant();
          Organization created =
              Organization.create(
                  UUID.randomUUID(),
                  command.name(),
                  command.contactName(),
                  command.email(),
                  command.phone(),
                  command.managers(),
                  support.subject(),
                  now);
          details.put("organizationId", created.id().toString());
          details.put("name", created.name());
          details.put("managersAdded", created.managers().size());
          organizations.insert(created);
          return organizations.find(created.id()).orElseThrow();
        });
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public Organization update(UUID id, long expectedVersion, OrganizationCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("organizationId", id.toString());
    return support.audited(
        UPDATE,
        details,
        () -> {
          Organization before = locked(id, expectedVersion);
          Organization after =
              before.update(
                  command.name(),
                  command.contactName(),
                  command.email(),
                  command.phone(),
                  command.managers(),
                  support.subject(),
                  clock.instant());
          details.put("name", after.name());
          Set<String> was = keys(before);
          Set<String> now = keys(after);
          details.put("managersAdded", now.stream().filter(k -> !was.contains(k)).count());
          details.put("managersRemoved", was.stream().filter(k -> !now.contains(k)).count());
          // Datos persoais do contacto: só que cambiaron.
          AuditChanges changes = new AuditChanges();
          changes.hidden("organization.contactName", before.contactName(), after.contactName());
          changes.hidden("organization.email", before.email(), after.email());
          changes.hidden("organization.phone", before.phone(), after.phone());
          changes.into(details);
          organizations.update(before, after);
          if (!before.name().equals(after.name())) {
            organizations.renameBookings(id, after.name());
          }
          return organizations.find(id).orElseThrow();
        });
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public boolean delete(UUID id, long expectedVersion) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("organizationId", id.toString());
    // A acción do rexistro depende da historia: lese baixo o bloqueo e despois rexístrase.
    Organization before;
    try {
      before = locked(id, expectedVersion);
    } catch (DomainException e) {
      return support.audited(
          DELETE,
          details,
          () -> {
            throw e;
          });
    }
    details.put("name", before.name());
    Instant now = clock.instant();
    if (!organizations.hasHistory(id)) {
      return support.audited(
          DELETE,
          details,
          () -> {
            organizations.delete(id);
            return false;
          });
    }
    return support.audited(
        ARCHIVE,
        details,
        () -> {
          if (organizations.hasFutureBookings(id, now)) {
            throw new DomainException(BookingError.ORGANIZATION_HAS_FUTURE_BOOKINGS);
          }
          details.put("managersRemoved", before.managers().size());
          requests.deletePending(id);
          organizations.update(before, before.archive(now));
          return true;
        });
  }

  /** A entidade dada de alta, coa fila bloqueada e a versión do {@code If-Match}. */
  private Organization locked(UUID id, long expectedVersion) {
    Organization found =
        organizations
            .findForUpdate(id)
            .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
    found.requireActive();
    if (found.version() == null || found.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return found;
  }

  private static Set<String> keys(Organization o) {
    Set<String> keys = new HashSet<>();
    o.managers().forEach(m -> keys.add(m.emailKey()));
    return keys;
  }
}
