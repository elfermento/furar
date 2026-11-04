// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Que se imprime (ADR-0026, PDF = `window.print()`): o panel enteiro, unha tarxeta de gráfico ou
 * o modal aberto. A folla de impresión (`styles/components/print.css`) le o alcance de `<html>`.
 */
export type PrintScope = 'panel' | 'card' | 'dialog';

const SCOPE = 'data-print';
const TARGET = 'data-print-target';

/**
 * Marca o alcance: `data-print` en `<html>` e, se hai un elemento concreto (a tarxeta), `data-print-target`
 * nel; a folla de impresión agocha o resto. Desfaise só ao rematar a impresión (`afterprint`), así
 * a vista previa do navegador xa sae co alcance. Devolve a función que o desfai antes (se a impresión
 * non chega a abrirse).
 */
export function setPrintScope(document: Document, scope: PrintScope, target?: Element | null): () => void {
  const root = document.documentElement;
  clear(document);
  root.setAttribute(SCOPE, scope);
  target?.setAttribute(TARGET, '');
  const view = document.defaultView;
  const done = () => {
    view?.removeEventListener('afterprint', done);
    clear(document);
  };
  view?.addEventListener('afterprint', done);
  return done;
}

/**
 * Imprime co alcance (a persoa escolle «Gardar como PDF» no diálogo do navegador). `before` corre
 * xa co alcance posto e antes de abrir a impresión (p. ex. cambiar os gráficos por imaxes).
 */
export async function printWithScope(
  document: Document,
  scope: PrintScope,
  target?: Element | null,
  before?: () => void | Promise<void>,
): Promise<void> {
  const view = document.defaultView;
  if (!view) return;
  const undo = setPrintScope(document, scope, target);
  try {
    await before?.();
    view.print();
  } catch (error) {
    undo();
    throw error;
  }
}

function clear(document: Document): void {
  document.documentElement.removeAttribute(SCOPE);
  for (const element of document.querySelectorAll(`[${TARGET}]`)) element.removeAttribute(TARGET);
}
