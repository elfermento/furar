// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * «Unirme» sen sesión: a persoa vai entrar e, ao volver xa con sesión á mesma partida, únese soa
 * (ou pide praza, se é con aprobación). A intención vai en `sessionStorage` (só nesta lapela), vale
 * unha vez e pouco tempo, e esquécese se a páxina se volve ver sen sesión (cancelou o acceso): así
 * nunca se une a ninguén que non premese «Unirme» xusto antes.
 */
export interface JoinIntent {
  readonly slug: string;
  readonly id: string;
  /** Cando se premeu (ms). */
  readonly at: number;
}

const KEY = 'furar.openMatchJoin';

/** Canto vale: o ir e vir do acceso (crear a conta incluída). */
export const JOIN_INTENT_TTL_MS = 10 * 60 * 1000;

export function saveJoinIntent(storage: Storage | undefined, intent: JoinIntent): void {
  try {
    storage?.setItem(KEY, JSON.stringify(intent));
  } catch {
    // Sen almacenamento (modo privado estrito): ao volver, preme «Unirme» outra vez.
  }
}

/** Esquece a intención (vista sen sesión, ou xa usada). */
export function clearJoinIntent(storage: Storage | undefined): void {
  try {
    storage?.removeItem(KEY);
  } catch {
    // Nada que borrar.
  }
}

/**
 * Colle a intención (e bórraa sempre): `true` só se é desta partida e non caducou. Unha
 * intención doutra partida tamén se borra: só vale para o regreso inmediato.
 */
export function takeJoinIntent(storage: Storage | undefined, slug: string, id: string, now: number): boolean {
  let intent: Partial<JoinIntent> | null;
  try {
    intent = JSON.parse(storage?.getItem(KEY) ?? 'null') as Partial<JoinIntent> | null;
  } catch {
    intent = null;
  }
  clearJoinIntent(storage);
  return (
    !!intent &&
    intent.slug === slug &&
    intent.id === id &&
    typeof intent.at === 'number' &&
    now - intent.at >= 0 &&
    now - intent.at <= JOIN_INTENT_TTL_MS
  );
}

/** O `sessionStorage` da lapela, se se pode usar (pode lanzar en modo privado estrito). */
export function tabStorage(view: Window | null | undefined): Storage | undefined {
  try {
    return view?.sessionStorage;
  } catch {
    return undefined;
  }
}
