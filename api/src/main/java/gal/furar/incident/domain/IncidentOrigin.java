// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

/**
 * Quen avisou: a veciñanza desde a ficha ou «As miñas reservas», ou o persoal desde a xestión
 * («Nova incidencia»). Só a da veciñanza recibe o correo ao resolverse.
 */
public enum IncidentOrigin {
  RESIDENT,
  STAFF
}
