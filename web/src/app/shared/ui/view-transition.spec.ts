// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inViewTransition, withViewTransition } from './view-transition';

/** O documento como rexistro: jsdom non ten a API e aquí ponse ou quítase a man. */
type Doc = Record<string, unknown>;

function setReducedMotion(reduce: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({ matches: reduce && query.includes('reduce'), media: query }),
  });
}

describe('withViewTransition', () => {
  const doc = document as unknown as Doc;
  const original = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  let tile: HTMLElement;
  let dialog: HTMLElement;

  beforeEach(() => {
    tile = document.body.appendChild(document.createElement('button'));
    dialog = document.createElement('div');
    setReducedMotion(false);
  });

  afterEach(() => {
    tile.remove();
    dialog.remove();
    Reflect.deleteProperty(doc, 'startViewTransition');
    if (original) Object.defineProperty(window, 'matchMedia', original);
    else Reflect.deleteProperty(window, 'matchMedia');
  });

  it('sen a API, fai o cambio directamente e non pon nomes', async () => {
    const update = vi.fn();
    await withViewTransition(document, update, { name: 'f-stat', from: tile, to: () => dialog });
    expect(update).toHaveBeenCalledOnce();
    expect(tile.style.getPropertyValue('view-transition-name')).toBe('');
  });

  it('con prefers-reduced-motion: reduce, tampouco anima aínda que haxa API', async () => {
    setReducedMotion(true);
    const start = vi.fn();
    doc['startViewTransition'] = start;
    const update = vi.fn();
    await withViewTransition(document, update);
    expect(update).toHaveBeenCalledOnce();
    expect(start).not.toHaveBeenCalled();
  });

  it('coa API: o nome pasa da orixe ao destino por CSSOM e quítase ao rematar', async () => {
    let finish!: () => void;
    const finished = new Promise<void>((resolve) => (finish = resolve));
    const seen: string[] = [];
    doc['startViewTransition'] = vi.fn((callback: () => Promise<void>) => {
      seen.push(`antes: ${tile.style.getPropertyValue('view-transition-name')}`);
      const updateCallbackDone = callback();
      return { updateCallbackDone, finished };
    });
    await withViewTransition(
      document,
      () => {
        document.body.append(dialog);
        seen.push(`cambio: ${tile.style.getPropertyValue('view-transition-name') || '-'}`);
      },
      { name: 'f-stat', from: tile, to: () => dialog },
    );
    expect(seen).toEqual(['antes: f-stat', 'cambio: -']);
    expect(dialog.style.getPropertyValue('view-transition-name')).toBe('f-stat');
    finish();
    await finished;
    await Promise.resolve();
    expect(dialog.style.getPropertyValue('view-transition-name')).toBe('');
  });

  it('nada se superpón: unha tecla ou pulsación durante a animación remátaa antes de chegar á páxina', async () => {
    let finish!: () => void;
    const finished = new Promise<void>((resolve) => (finish = resolve));
    const skip = vi.fn(() => finish());
    const ready = Promise.reject(new DOMException('Transition was skipped', 'AbortError'));
    doc['startViewTransition'] = vi.fn((callback: () => Promise<void>) => ({
      updateCallbackDone: callback(),
      finished,
      ready,
      skipTransition: skip,
    }));
    const seen: string[] = [];
    const page = (event: Event) => seen.push(`${event.type}: ${inViewTransition(document) ? 'en curso' : 'rematada'}`);
    document.addEventListener('keydown', page);
    try {
      await withViewTransition(document, () => undefined, { name: 'f-stat', from: tile, to: () => dialog });
      expect(inViewTransition(document)).toBe(true);
      tile.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      expect(skip).toHaveBeenCalledOnce();
      await finished;
      await Promise.resolve();
      expect(inViewTransition(document)).toBe(false);
      // Xa rematada, as teclas seguen o seu camiño sen tocar nada.
      tile.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      tile.dispatchEvent(new Event('pointerdown', { bubbles: true }));
      expect(skip).toHaveBeenCalledOnce();
      expect(seen.at(-1)).toBe('keydown: rematada');
    } finally {
      document.removeEventListener('keydown', page);
    }
  });

  it('unha transición nova remata a anterior primeiro', async () => {
    const skips: string[] = [];
    let n = 0;
    doc['startViewTransition'] = vi.fn((callback: () => Promise<void>) => {
      const id = `t${++n}`;
      return {
        updateCallbackDone: callback(),
        finished: new Promise<void>(() => undefined),
        skipTransition: () => skips.push(id),
      };
    });
    await withViewTransition(document, () => undefined);
    await withViewTransition(document, () => undefined);
    expect(skips).toEqual(['t1']);
  });
});
