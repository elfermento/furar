// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

/**
 * Estado dunha incidencia (ADR-0036): Nova, En curso ou Resolta. Pódese ir de calquera a calquera.
 */
public enum IncidentStatus {
  NEW,
  IN_PROGRESS,
  RESOLVED
}
