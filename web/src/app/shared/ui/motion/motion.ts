// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Movemento das vistas lista → detalle (as reservas do concello e a vista previa do tema): a mesma
 * curva e duración en todas, e nada se move se a persoa pediu menos movemento.
 */

/** Curva das capas (`--ease-smooth`). */
const EASE = 'cubic-bezier(0.22, 1, 0.36, 1)';
/** Canto do desprazamento real se ve ao mover unha sección (suavizado). */
const SOFTEN = 0.35;
const DURATION_MS = 320;

/** Se se pode animar: o navegador ten Web Animations e a persoa non pediu menos movemento. */
export function motionAllowed(view: Window | null, element: Element): boolean {
  return (
    typeof (element as HTMLElement).animate === 'function' &&
    !(view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false)
  );
}

/**
 * FLIP suavizado: a sección xa está no seu sitio novo e entra desde onde estaba (`before`), só
 * cun 35 % do camiño e medio transparente, para que se vexa de onde vén sen mareo.
 */
export function slideFrom(view: Window | null, element: HTMLElement, before: DOMRect): void {
  const dx = before.left - element.getBoundingClientRect().left;
  if (!dx || !motionAllowed(view, element)) return;
  element.animate(
    [
      { transform: `translateX(${dx * SOFTEN}px)`, opacity: 0.6 },
      { transform: 'none', opacity: 1 },
    ],
    { duration: DURATION_MS, easing: EASE },
  );
}

/** A sección que volve a aparecer ao pechar o detalle: entra cun fundido desde a dereita. */
export function fadeIn(view: Window | null, element: HTMLElement): void {
  if (!motionAllowed(view, element)) return;
  element.animate(
    [
      { transform: 'translateX(0.75rem)', opacity: 0 },
      { transform: 'none', opacity: 1 },
    ],
    { duration: DURATION_MS, easing: EASE },
  );
}

/** O detalle vaise cara á dereita; `done` corre ao rematar (ou xa, sen movemento). */
export function slideOut(view: Window | null, element: HTMLElement | undefined, done: () => void): void {
  if (!element || !motionAllowed(view, element)) {
    done();
    return;
  }
  element.animate(
    [
      { opacity: 1, transform: 'none' },
      { opacity: 0, transform: 'translateX(1rem)' },
    ],
    { duration: 180, easing: 'ease-in' },
  ).onfinish = done;
}

/**
 * Transformación que leva `to` a ocupar `from` (FLIP): a escala e o desprazamento desde a esquina
 * superior esquerda.
 */
function flip(from: DOMRect, to: DOMRect): string {
  const sx = to.width ? from.width / to.width : 1;
  const sy = to.height ? from.height / to.height : 1;
  return `translate(${from.left - to.left}px, ${from.top - to.top}px) scale(${sx}, ${sy})`;
}

/**
 * O panel medra desde o botón que o abre (FLIP): empeza co tamaño e o sitio do botón e chega ao
 * seu, coa mesma curva e duración ca as reservas.
 */
export function growFrom(view: Window | null, element: HTMLElement, origin: DOMRect): void {
  if (!motionAllowed(view, element)) return;
  const end = element.getBoundingClientRect();
  if (!end.width || !end.height) return;
  element.animate(
    [
      { transformOrigin: 'top left', transform: flip(origin, end), opacity: 0.4 },
      { transformOrigin: 'top left', transform: 'none', opacity: 1 },
    ],
    { duration: DURATION_MS, easing: EASE },
  );
}

/** Ao pechar, ao revés: o panel encolle ata o botón e despois `done` (xa, sen movemento). */
export function shrinkInto(
  view: Window | null,
  element: HTMLElement | undefined,
  target: () => DOMRect | null,
  done: () => void,
): void {
  const to = target();
  if (!element || !to || !motionAllowed(view, element)) {
    done();
    return;
  }
  const start = element.getBoundingClientRect();
  element.animate(
    [
      { transformOrigin: 'top left', transform: 'none', opacity: 1 },
      { transformOrigin: 'top left', transform: flip(to, start), opacity: 0 },
    ],
    { duration: 220, easing: 'ease-in', fill: 'forwards' },
  ).onfinish = done;
}
