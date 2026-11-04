// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Elemento que «se transforma» noutro durante a transición (a tarxeta no modal): `from` leva o
 * nome antes do cambio e `to()` o leva despois (búscase xa co DOM novo).
 */
export interface ViewTransitionMorph {
  readonly name: string;
  readonly from?: HTMLElement | null;
  readonly to?: () => HTMLElement | null | undefined;
}

const PROPERTY = 'view-transition-name';

interface TransitionLike {
  readonly updateCallbackDone: Promise<void>;
  readonly finished: Promise<void>;
  readonly ready?: Promise<void>;
  skipTransition?(): void;
}

type StartViewTransition = (update: () => Promise<void>) => TransitionLike;

/** A transición en curso de cada documento (unha como moito: unha nova remata a anterior). */
const running = new WeakMap<Document, TransitionLike>();

/**
 * O que remata a transición en curso antes de chegar á páxina: teclas, punteiro e texto escrito
 * (un `input` sen tecla, como o autocompletado, tamén abre listas).
 */
const INPUT = ['keydown', 'pointerdown', 'input'] as const;

/** Hai API e a persoa non pediu menos movemento. */
export function canTransition(document: Document): boolean {
  const start = (document as Document & { startViewTransition?: StartViewTransition }).startViewTransition;
  const reduce = document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
  return typeof start === 'function' && !reduce;
}

/** Hai unha transición en curso (abrindo ou pechando unha capa). */
export function inViewTransition(document: Document): boolean {
  return running.has(document);
}

/** Remata xa a transición en curso, se a hai: o DOM queda no estado final. */
export function finishViewTransition(document: Document): void {
  running.get(document)?.skipTransition?.();
}

/**
 * Fai `update` cunha View Transition se o navegador a ten e a persoa non pediu menos movemento;
 * se non, chámaa directamente (abre igual, sen animación). O nome vai por CSSOM (`style.setProperty`,
 * que a CSP `style-src 'self'` permite) e quítase ao rematar. A promesa resólvese cando o DOM xa
 * cambiou (para mover o foco), non cando remata a animación.
 *
 * Nada se superpón a unha transición: mentres dura, a primeira tecla, pulsación ou texto remátaa (salta
 * ao estado final) antes de que a páxina a reciba, e unha transición nova remata a anterior. Abrir
 * outra capa (o menú «Exportar» co teclado) coa animación de peche aínda en curso tumbaba o
 * renderizador de Chromium.
 */
export async function withViewTransition(
  document: Document,
  update: () => void | Promise<void>,
  morph?: ViewTransitionMorph,
): Promise<void> {
  if (!canTransition(document)) {
    await update();
    return;
  }
  finishViewTransition(document);
  const start = (document as Document & { startViewTransition: StartViewTransition }).startViewTransition;
  morph?.from?.style.setProperty(PROPERTY, morph.name);
  let target: HTMLElement | null | undefined;
  const transition = start.call(document, async () => {
    morph?.from?.style.removeProperty(PROPERTY);
    await update();
    target = morph?.to?.();
    if (morph) target?.style.setProperty(PROPERTY, morph.name);
  });
  running.set(document, transition);
  const view = document.defaultView;
  const skip = () => transition.skipTransition?.();
  for (const type of INPUT) view?.addEventListener(type, skip, { capture: true });
  const done = () => {
    for (const type of INPUT) view?.removeEventListener(type, skip, { capture: true });
    if (running.get(document) === transition) running.delete(document);
    target?.style.removeProperty(PROPERTY);
  };
  // Saltala (outra transición, unha tecla) rexeita `ready`: é o previsto, non un erro.
  transition.ready?.catch(() => undefined);
  transition.finished.then(done, done);
  await transition.updateCallbackDone;
}
