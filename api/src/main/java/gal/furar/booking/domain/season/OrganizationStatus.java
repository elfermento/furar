// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

/**
 * Estado dunha entidade na táboa (ADR-0039, punto 4), calculado ao ler: con algunha solicitude
 * pendente, {@link #REQUEST}; se non, con algunha sesión confirmada futura, {@link #SEASON}; se
 * non, {@link #NONE}.
 */
public enum OrganizationStatus {
  REQUEST,
  SEASON,
  NONE;

  public static OrganizationStatus of(boolean pendingRequest, boolean futureSession) {
    if (pendingRequest) {
      return REQUEST;
    }
    return futureSession ? SEASON : NONE;
  }
}
