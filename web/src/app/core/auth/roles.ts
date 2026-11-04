// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Roles da interface. Ningún sae do token (ADR-0028): o rol de persoal do concello dao a API
 * (`GET …/me/staff`, ADR-0027) e a administración da plataforma, tamén (`GET /api/v1/me/platform-access`).
 */
export type Role = 'PLATFORM_ADMIN' | 'MANAGER' | 'STAFF' | 'RESIDENT';

/** Rol de persoal nun concello, tal como o devolve a API (`GET …/me/staff`). */
export type StaffRole = 'MANAGER' | 'STAFF';

/**
 * Só para a interface (amosar ou agochar a xestión). Quen decide é a API: o permiso vive no caso
 * de uso (ADR-0007). O rol de persoal sae da API (ADR-0027), non do claim. A xestión completa do
 * concello (reservas e indicadores incluídos) é só do xestor.
 */
export function canManage(role: StaffRole | null): boolean {
  return role === 'MANAGER';
}

/**
 * Se pode configurar o concello: os seus datos, instalacións, campos personalizados, persoal e
 * rexistro de actividade. O xestor e a administración da plataforma, que deixa o concello feito
 * (ADR-0028, revisión do 2026-11-03); a plataforma non ve reservas, indicadores nin a exportación.
 */
export function canConfigure(role: StaffRole | null, platformAdmin: boolean): boolean {
  return canManage(role) || platformAdmin;
}

/**
 * Se pode ver as reservas do concello e marcar cobros (permisos de ver e de cobrar da API): a
 * xestión e o persoal do mostrador (STAFF). Só para a interface.
 */
export function canViewBookings(role: StaffRole | null): boolean {
  return role === 'MANAGER' || role === 'STAFF';
}

/**
 * O rol que se mostra na barra para ese concello: administración da plataforma, se a ten; se non,
 * o de persoal que devolve a API. Só para a interface.
 */
export function mainRole(platformAdmin: boolean, role: StaffRole | null): Role | null {
  return platformAdmin ? 'PLATFORM_ADMIN' : role;
}

/** A alta dun concello: a primeira entrada da plataforma e «Novo concello» no seu menú. */
export const NEW_MUNICIPALITY_URL = '/plataforma/concellos/novo';

/**
 * Onde entra o persoal na xestión dun concello: o xestor, a sección «Concello» (a súa primeira
 * pestana); o persoal do mostrador, as reservas. Igual despois de aceptar a invitación e ao
 * cambiar de concello no menú.
 */
export function managementHome(slug: string, role: StaffRole): string {
  return role === 'STAFF' ? `/${slug}/xestion/reservas` : `/${slug}/xestion/concello`;
}
