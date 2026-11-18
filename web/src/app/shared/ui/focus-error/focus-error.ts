// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** O que pode levar o foco dentro dun campo con erro (o propio control ou o primeiro de dentro). */
const FOCUSABLE =
  'input:not([type="hidden"]):not(:disabled), select:not(:disabled), textarea:not(:disabled), button:not(:disabled), [contenteditable="true"], [tabindex]:not([tabindex="-1"])';

/** Avisos de erro que non son dun campo (furar-problem-alert, furar-form-errors-summary) con texto. */
const ALERT = '.f-alert-slot:not([data-empty]) [role="alert"]';

function focusable(element: HTMLElement): HTMLElement | null {
  return element.matches(FOCUSABLE) ? element : element.querySelector<HTMLElement>(FOCUSABLE);
}

/** Non escondido (`hidden`, `inert`); dentro dun `<details>` pechado si conta: ábrese. */
function reachable(element: HTMLElement): boolean {
  return !element.closest('[hidden], [inert]');
}

function before(a: Node, b: Node): number {
  return a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1;
}

/**
 * Primeiro campo con erro do contedor na orde de lectura: o primeiro que leva `aria-invalid="true"`
 * (o erro á vista); se ningún o leva, o primeiro control de formulario `ng-invalid` xa tocado (unha
 * folla, sen outro inválido dentro; un grupo inválido polos seus fillos non conta).
 */
export function firstFieldError(container: HTMLElement): HTMLElement | null {
  const pick = (elements: HTMLElement[]) =>
    elements
      .sort(before)
      .map(focusable)
      .find((element): element is HTMLElement => !!element && reachable(element)) ?? null;
  return (
    pick([...container.querySelectorAll<HTMLElement>('[aria-invalid="true"]')]) ??
    pick(
      [...container.querySelectorAll<HTMLElement>('.ng-invalid.ng-touched')].filter(
        (element) => !element.querySelector('.ng-invalid'),
      ),
    )
  );
}

/** O erro que describe o control (o primeiro id de `aria-describedby` cunha mensaxe de erro). */
function errorMessageOf(control: HTMLElement): HTMLElement | null {
  const document = control.ownerDocument;
  for (const id of (control.getAttribute('aria-describedby') ?? '').split(/\s+/).filter(Boolean)) {
    const element = document.getElementById(id);
    if (element?.matches('.f-field-error, .field-error, [role="alert"]')) {
      return element;
    }
  }
  return null;
}

function reducedMotion(element: HTMLElement): boolean {
  const html = element.ownerDocument.documentElement;
  const view = element.ownerDocument.defaultView;
  return html.dataset['motion'] === 'reduce' || !!view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Leva o foco a `target` e déixao á vista coa súa mensaxe: abre os `<details>` que o tapan, centra
 * o campo (a súa cápsula) e despraza o xusto para que se vexa tamén o erro de debaixo. O lector de
 * pantalla anúnciao ao recibir o foco: o rótulo, «non válido» e o erro (`aria-describedby`).
 */
export function revealAndFocus(target: HTMLElement): void {
  for (let details = target.closest('details'); details; details = details.parentElement?.closest('details') ?? null) {
    details.open = true;
  }
  target.focus({ preventScroll: true });
  const behavior: ScrollBehavior = reducedMotion(target) ? 'auto' : 'smooth';
  const unit = target.closest<HTMLElement>('.f-field, .f-bilingual, .f-fields, .f-field-popover, .f-switch') ?? target;
  const message = errorMessageOf(target);
  // Un só desprazamento (dous suaves seguidos cancélanse e o campo quedaba fóra): o campo e a súa
  // mensaxe xuntos se caben (a mensaxe dun grupo vai debaixo del), se non o campo.
  const both = message && !unit.contains(message) ? holderOf(unit, message) : null;
  (both ?? unit).scrollIntoView?.({ block: 'center', inline: 'nearest', behavior });
}

/** O menor antecesor de `unit` que contén `message`, se cabe na pantalla; se non, `null`. */
function holderOf(unit: HTMLElement, message: HTMLElement): HTMLElement | null {
  let holder: HTMLElement | null = unit.parentElement;
  while (holder && !holder.contains(message)) {
    holder = holder.parentElement;
  }
  const view = unit.ownerDocument.defaultView;
  const room = (view?.innerHeight ?? 0) * 0.7;
  return holder && holder.getBoundingClientRect().height <= room ? holder : null;
}

/**
 * Foco no primeiro campo con erro do contedor, na orde de lectura, á vista e coa súa mensaxe
 * (`revealAndFocus`). Devolve se o atopou.
 */
export function focusFirstError(container: HTMLElement): boolean {
  const target = firstFieldError(container);
  if (target) {
    revealAndFocus(target);
  }
  return !!target;
}

/**
 * Ao fallar un gardado: foco no primeiro campo con erro; se o erro non é dun campo (o servidor),
 * no aviso de erro do contedor (`furar-problem-alert`, `furar-form-errors-summary`). Devolve se
 * moveu o foco.
 */
export function focusErrorOrAlert(container: HTMLElement): boolean {
  if (focusFirstError(container)) {
    return true;
  }
  const alert = [...container.querySelectorAll<HTMLElement>(ALERT)].find((element) => element.textContent?.trim());
  if (!alert) {
    return false;
  }
  if (!alert.hasAttribute('tabindex')) {
    alert.tabIndex = -1;
  }
  revealAndFocus(alert);
  return true;
}
