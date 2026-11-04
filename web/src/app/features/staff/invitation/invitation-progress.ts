// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Onde vai a invitación mentres a persoa crea a conta ou entra en Keycloak: en `sessionStorage`,
 * só nesta lapela e ata pechala. O token non vai nunca no URL nin no historial.
 */
export interface InvitationProgress {
  readonly slug: string;
  readonly token: string;
  /**
   * `keycloak`: xa se levou a persoa a Keycloak unha vez con este token. Se volve sen sesión
   * (cancelou ou houbo un erro), non se redirixe outra vez soa: vese un botón (sen bucle).
   */
  readonly stage?: 'keycloak';
}

const KEY = 'furar.invitation';

/** Un token de 32 bytes en base64url ten 43 caracteres; admítese algo de marxe. */
const TOKEN = /^[A-Za-z0-9_-]{20,128}$/;

export function isToken(value: string | null | undefined): value is string {
  return !!value && TOKEN.test(value);
}

export function readProgress(storage: Storage | undefined, slug: string): InvitationProgress | null {
  try {
    const value: unknown = JSON.parse(storage?.getItem(KEY) ?? 'null');
    if (typeof value !== 'object' || value === null) return null;
    const progress = value as InvitationProgress;
    return progress.slug === slug && isToken(progress.token) ? progress : null;
  } catch {
    return null;
  }
}

export function saveProgress(storage: Storage | undefined, progress: InvitationProgress): void {
  try {
    storage?.setItem(KEY, JSON.stringify(progress));
  } catch {
    // Sen almacenamento (modo privado estrito): a invitación vale só mentres non se saia da páxina.
  }
}

export function clearProgress(storage: Storage | undefined): void {
  try {
    storage?.removeItem(KEY);
  } catch {
    // Nada que borrar.
  }
}
