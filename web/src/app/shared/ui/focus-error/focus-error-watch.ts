// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { focusErrorOrAlert } from './focus-error';

/** Canto se agarda a resposta do servidor despois de enviar (os erros que chegan máis tarde, non). */
export const FOCUS_ERROR_WINDOW_MS = 30_000;

/** O que fai a persoa e corta a espera: desde aí, un erro que chegue non lle move o foco. */
const USER_EVENTS = ['keydown', 'pointerdown', 'input'] as const;

function errorAppeared(records: MutationRecord[]): boolean {
  return records.some((record) => {
    const target = record.target as Element;
    return record.attributeName === 'aria-invalid'
      ? target.getAttribute('aria-invalid') === 'true'
      : record.attributeName === 'data-empty' && !target.hasAttribute('data-empty');
  });
}

/**
 * Despois de enviar un formulario do contedor (xa pintado): foco no primeiro campo con erro ou no
 * aviso de erro (`focusErrorOrAlert`). Se aínda non hai ningún, agarda a resposta do servidor:
 * o primeiro campo que pase a `aria-invalid="true"` ou o aviso que apareza levan o foco, unha
 * vez, mentres a persoa non toque nada. Devolve como deixar de agardar.
 */
export function focusErrorsAfterSubmit(host: HTMLElement): () => void {
  const Observer = host.ownerDocument.defaultView?.MutationObserver;
  if (focusErrorOrAlert(host) || !Observer) {
    return () => undefined;
  }
  const observer = new Observer((records) => {
    if (errorAppeared(records) && focusErrorOrAlert(host)) {
      stop();
    }
  });
  const timer = setTimeout(() => stop(), FOCUS_ERROR_WINDOW_MS);
  function stop(): void {
    observer.disconnect();
    clearTimeout(timer);
    for (const type of USER_EVENTS) {
      host.removeEventListener(type, stop, true);
    }
  }
  observer.observe(host, { subtree: true, attributes: true, attributeFilter: ['aria-invalid', 'data-empty'] });
  for (const type of USER_EVENTS) {
    host.addEventListener(type, stop, true);
  }
  return stop;
}
