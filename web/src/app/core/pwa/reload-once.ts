// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import type { NavigationError } from '@angular/router';

const KEY = 'furar.reloadedAt';
/** Dúas recargas seguidas dentro deste tempo serían un bucle: a segunda non se fai. */
export const RELOAD_WINDOW_MS = 60_000;

/**
 * Erro ao cargar o código dunha páxina (`import()` diferido): tras un despregue novo, o *chunk* que
 * pide a versión aberta xa non existe (Caddy dá 404) ou falla a rede. Mensaxes de Chromium, Firefox
 * e Safari.
 */
export function isChunkLoadError(error: unknown): boolean {
  const text = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  return /dynamically imported module|Importing a module script failed|ChunkLoadError/i.test(text);
}

/**
 * Recarga a páxina para coller a versión publicada, como moito unha vez cada `RELOAD_WINDOW_MS`
 * (marca en `sessionStorage`): se despois de recargar segue fallando, non hai bucle e queda o erro
 * de sempre. Sen `sessionStorage` non recarga, porque non podería evitar o bucle.
 */
export function reloadOnce(view: Window | null, now = Date.now()): boolean {
  if (!view) return false;
  try {
    const last = Number(view.sessionStorage.getItem(KEY) ?? Number.NaN);
    if (now - last < RELOAD_WINDOW_MS) return false;
    view.sessionStorage.setItem(KEY, String(now));
  } catch {
    return false;
  }
  view.location.reload();
  return true;
}

/** `withNavigationErrorHandler`: un *chunk* perdido recarga a páxina (unha vez). */
export function reloadOnChunkError(error: NavigationError): void {
  if (isChunkLoadError(error.error)) reloadOnce(inject(DOCUMENT).defaultView);
}
