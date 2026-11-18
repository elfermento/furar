// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

/**
 * Pide praza, ten praza, non a aceptaron, espera na lista ou foi avisada dunha praza libre
 * (ADR-0041).
 */
public enum EventRegistrationStatusValue {
  PENDING,
  CONFIRMED,
  REJECTED,
  WAITING,
  NOTIFIED
}
