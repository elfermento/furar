// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.OrganizationStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As entidades do concello e os seus responsables (ADR-0039, punto 7; permiso {@code
 * booking.organizations}). Cada alta, cambio, arquivo e baixa queda no rexistro de actividade sen
 * os correos.
 */
public interface ManageOrganizationsUseCase {

  /** As dadas de alta (ou as arquivadas), por nome; cun estado, só as dese estado. */
  List<OrganizationRow> list(@Nullable OrganizationStatus status, boolean archived);

  /** Unha entidade, tamén arquivada; se non, 404 {@code booking.organization-not-found}. */
  Organization get(UUID id);

  /**
   * 409 {@code booking.organization-name-taken}; 422 {@code booking.organization-managers-limit}.
   */
  Organization create(OrganizationCommand command);

  /**
   * Os datos novos (If-Match). Cambiar o nome cámbiao tamén nas súas reservas.
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.stale-version}, {@code
   *     booking.organization-name-taken}, {@code booking.organization-managers-limit} ou {@code
   *     booking.organization-not-found} (arquivada)
   */
  Organization update(UUID id, long expectedVersion, OrganizationCommand command);

  /**
   * Sen historia, bórraa; con ela, arquívaa (queda o nome). Con sesións ou reservas futuras, 409
   * {@code booking.organization-has-future-bookings}.
   *
   * @return verdadeiro se a arquivou
   */
  boolean delete(UUID id, long expectedVersion);
}
