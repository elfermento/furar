// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

/**
 * Estado dunha pertenza (plan do dominio 4, §1.2.8; ADR-0027, revisión do 2026-10-30): {@code
 * INVITED → ACTIVE}, {@code INVITED|ACTIVE → DEACTIVATED} e {@code DEACTIVATED → ACTIVE}
 * (reactivar, se aceptou algunha vez e non está anonimizada). Unha invitada desactivada invítase de
 * novo, nunha pertenza nova.
 */
public enum StaffStatus {
  INVITED,
  ACTIVE,
  DEACTIVATED
}
