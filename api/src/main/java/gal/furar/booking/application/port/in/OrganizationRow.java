// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.OrganizationStatus;
import java.util.Objects;

/** Unha fila da táboa de entidades: a entidade, cantos responsables ten e o seu estado. */
public record OrganizationRow(Organization organization, int managers, OrganizationStatus status) {

  public OrganizationRow {
    Objects.requireNonNull(organization, "organization");
    Objects.requireNonNull(status, "status");
  }
}
