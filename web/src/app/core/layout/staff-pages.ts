// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Páxinas do persoal que xa existen: os menús non ligan a rutas que aínda non hai. */
export interface StaffPages {
  /** «Persoal» (`/:concello/xestion/persoal`, `features/staff/members`). */
  readonly members: boolean;
  /** «Rexistro de actividade» (`/:concello/xestion/rexistro` e `/plataforma/rexistro`, `features/staff/activity`). */
  readonly activity: boolean;
}

/**
 * Bandeira mentres chegan as páxinas: cada tarefa que engade a súa ruta pona a `true` (D4-14
 * «Persoal», D4-16 «Rexistro de actividade»). Ata entón, nin os menús nin a creación dun concello
 * levan a elas.
 */
export const STAFF_PAGES: StaffPages = { members: true, activity: true };
