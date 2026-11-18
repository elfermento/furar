// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Estado dunha entidade na táboa (ADR-0039, punto 4). */
public enum OrganizationStatusValue {
  /** Ten algunha solicitude de tempada pendente. */
  REQUEST,
  /** Ten sesións confirmadas futuras. */
  SEASON,
  NONE
}
